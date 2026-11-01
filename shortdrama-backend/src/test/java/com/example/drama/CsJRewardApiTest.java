package com.example.drama;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:csjrewardtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.flyway.enabled=false",
        "app.admin-password=AdminTest123!",
        "app.rewards.csj.enabled=true",
        "app.rewards.csj.attempt-ttl=PT10M",
        "app.rewards.csj.android.enabled=true",
        "app.rewards.csj.android.app-id=android-test-app",
        "app.rewards.csj.android.placement-id=android-test-placement",
        "app.rewards.csj.android.security-key=android-test-secret",
        "app.rewards.csj.android.reward-name=episode_unlock",
        "app.rewards.csj.android.reward-amount=1",
        "app.rewards.csj.ios.enabled=false"
})
class CsJRewardApiTest {
    private static final String USER_A = "csj_reward_a";
    private static final String USER_B = "csj_reward_b";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AppUserRepository users;
    @Autowired DramaRepository dramas;
    @Autowired RewardAttemptRepository attempts;
    @Autowired EpisodeUnlockRepository unlocks;
    @Autowired CsJRewardService rewardService;

    private AppUser userA;
    private AppUser userB;
    private Episode lockedEpisode;

    @BeforeEach
    void prepare() {
        userA = createUser(USER_A);
        userB = createUser(USER_B);
        lockedEpisode = dramas.findAll().stream()
                .filter(drama -> drama.getStatus() == DramaStatus.PUBLISHED)
                .flatMap(drama -> drama.getEpisodes().stream())
                .filter(episode -> !episode.isFree())
                .findFirst().orElseThrow();
    }

    @AfterEach
    void cleanup() {
        attempts.deleteAll();
        unlocks.deleteAll();
        users.deleteById(userA.getId());
        users.deleteById(userB.getId());
    }

    @Test
    void configIsAuthenticatedSanitizedAndAttemptRequiresCsrf() throws Exception {
        mvc.perform(get("/api/rewards/csj/config"))
                .andExpect(status().isUnauthorized());

        MvcResult config = mvc.perform(get("/api/rewards/csj/config").with(user(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.platforms.ANDROID.enabled", is(true)))
                .andExpect(jsonPath("$.platforms.ANDROID.configured", is(true)))
                .andExpect(jsonPath("$.platforms.ANDROID.appId", is("android-test-app")))
                .andExpect(jsonPath("$.platforms.IOS.enabled", is(false)))
                .andReturn();
        String body = config.getResponse().getContentAsString();
        assertFalse(body.contains("android-test-secret"));
        assertFalse(body.toLowerCase().contains("securitykey"));

        mvc.perform(post("/api/rewards/csj/attempts").with(user(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(lockedEpisode.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rewards/csj/attempts").with(user(USER_A)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"episodeId\":" + lockedEpisode.getId() + ",\"platform\":\"IOS\"}"))
                .andExpect(status().isServiceUnavailable());
        assertEquals("7690c4781c0a1d5c9060238dabb0388a7b53ed6eb3bdc9e356a7aa8f29017abd",
                CsJRewardService.signature("android-test-secret", "csj-known-transaction"));
    }

    @Test
    void validServerCallbackGrantsOnceAndStatusIsOwnerOnly() throws Exception {
        JsonNode attempt = createAttempt(USER_A);
        String transactionId = "csj-transaction-valid";

        validCallback(attempt, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)));
        assertTrue(unlocks.existsByUserIdAndEpisodeId(userA.getId(), lockedEpisode.getId()));

        mvc.perform(get("/api/rewards/csj/attempts/{id}", attempt.get("id").asText()).with(user(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("GRANTED")))
                .andExpect(jsonPath("$.episodeId", is(lockedEpisode.getId().intValue())));
        mvc.perform(get("/api/rewards/csj/attempts/{id}", attempt.get("id").asText()).with(user(USER_B)))
                .andExpect(status().isNotFound());

        validCallback(attempt, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)));
        assertTrue(unlocks.findByUserIdOrderByGrantedAtDesc(userA.getId()).stream()
                .filter(unlock -> unlock.getEpisode().getId().equals(lockedEpisode.getId())).count() == 1);
    }

    @Test
    void invalidExpiredAndCrossAttemptCallbacksNeverGrant() throws Exception {
        JsonNode attemptA = createAttempt(USER_A);
        JsonNode attemptB = createAttempt(USER_B);
        String transactionId = "csj-transaction-replay";

        callback(attemptA, transactionId, "wrong_reward", "1",
                CsJRewardService.signature("android-test-secret", transactionId))
                .andExpect(jsonPath("$.isValid", is(false)));
        callback(attemptA, transactionId, "episode_unlock", "1", "0".repeat(64))
                .andExpect(jsonPath("$.isValid", is(false)));
        assertFalse(unlocks.existsByUserIdAndEpisodeId(userA.getId(), lockedEpisode.getId()));

        validCallback(attemptA, transactionId)
                .andExpect(jsonPath("$.isValid", is(true)));
        callback(attemptB, transactionId, "episode_unlock", "1",
                CsJRewardService.signature("android-test-secret", transactionId))
                .andExpect(jsonPath("$.isValid", is(false)));
        assertFalse(unlocks.existsByUserIdAndEpisodeId(userB.getId(), lockedEpisode.getId()));

        RewardAttempt expired = attempts.findById(attemptB.get("id").asText()).orElseThrow();
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        attempts.saveAndFlush(expired);
        String expiredTransaction = "csj-transaction-expired";
        callback(attemptB, expiredTransaction, "episode_unlock", "1",
                CsJRewardService.signature("android-test-secret", expiredTransaction))
                .andExpect(jsonPath("$.isValid", is(false)));
        mvc.perform(get("/api/rewards/csj/attempts/{id}", expired.getId()).with(user(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("EXPIRED")));
        assertFalse(unlocks.existsByUserIdAndEpisodeId(userB.getId(), lockedEpisode.getId()));
    }

    @Test
    void concurrentDuplicateCallbackIsIdempotent() throws Exception {
        JsonNode attempt = createAttempt(USER_A);
        String transactionId = "csj-transaction-concurrent";
        String sign = CsJRewardService.signature("android-test-secret", transactionId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return rewardService.callback(attempt.get("providerUserId").asText(), transactionId,
                        "episode_unlock", "1", attempt.get("extra").asText(), sign);
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return rewardService.callback(attempt.get("providerUserId").asText(), transactionId,
                        "episode_unlock", "1", attempt.get("extra").asText(), sign);
            });
            start.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, unlocks.findByUserIdOrderByGrantedAtDesc(userA.getId()).stream()
                .filter(unlock -> unlock.getEpisode().getId().equals(lockedEpisode.getId())).count());
    }

    private JsonNode createAttempt(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/rewards/csj/attempts").with(user(username)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(lockedEpisode.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.platform", is("ANDROID")))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions validCallback(
            JsonNode attempt, String transactionId) throws Exception {
        return callback(attempt, transactionId, "episode_unlock", "1",
                CsJRewardService.signature("android-test-secret", transactionId));
    }

    private org.springframework.test.web.servlet.ResultActions callback(
            JsonNode attempt, String transactionId, String rewardName, String rewardAmount, String sign)
            throws Exception {
        return mvc.perform(get("/api/rewards/csj/callback")
                        .param("user_id", attempt.get("providerUserId").asText())
                        .param("trans_id", transactionId)
                        .param("reward_name", rewardName)
                        .param("reward_amount", rewardAmount)
                        .param("extra", attempt.get("extra").asText())
                        .param("sign", sign))
                .andExpect(status().isOk());
    }

    private String attemptJson(Long episodeId) {
        return "{\"episodeId\":" + episodeId + ",\"platform\":\"ANDROID\"}";
    }

    private AppUser createUser(String username) {
        return users.findByUsernameIgnoreCase(username).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setUsername(username);
            created.setNickname(username);
            created.setPasswordHash("unused-test-password-hash");
            created.setRole(UserRole.USER);
            return users.saveAndFlush(created);
        });
    }
}
