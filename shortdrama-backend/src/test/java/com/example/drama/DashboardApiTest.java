package com.example.drama;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboardtest;DB_CLOSE_DELAY=-1",
        "app.admin-password=AdminTest123!"
})
@AutoConfigureMockMvc
class DashboardApiTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired EpisodeRepository episodes;
    @Autowired PlaybackEventRepository playbackEvents;
    @Autowired PaymentOrderRepository paymentOrders;
    @Autowired RewardAttemptRepository rewardAttempts;

    @Test
    void dashboardUsesRealEventsDateBoundariesAndIdempotentPlayback() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(2);
        Instant fromStart = from.atStartOfDay(ZONE).toInstant();
        Instant todayStart = today.atStartOfDay(ZONE).toInstant();
        Instant endExclusive = today.plusDays(1).atStartOfDay(ZONE).toInstant();

        MockHttpSession firstSession = register("dashboard_first");
        MockHttpSession secondSession = register("dashboard_second");
        MockHttpSession adminSession = login("admin", "AdminTest123!");
        AppUser first = users.findByUsernameIgnoreCase("dashboard_first").orElseThrow();
        AppUser second = users.findByUsernameIgnoreCase("dashboard_second").orElseThrow();
        AppUser admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
        jdbc.update("UPDATE app_user SET created_at = ?, membership_expires_at = ? WHERE id = ?",
                LocalDateTime.ofInstant(fromStart, ZoneOffset.UTC),
                LocalDateTime.ofInstant(Instant.now().plusSeconds(86_400), ZoneOffset.UTC), first.getId());
        jdbc.update("UPDATE app_user SET created_at = NULL, membership_expires_at = ? WHERE id = ?",
                LocalDateTime.ofInstant(Instant.now().minusSeconds(60), ZoneOffset.UTC), second.getId());

        UUID clientEventId = UUID.randomUUID();
        String playbackJson = json(Map.of("eventId", clientEventId, "episodeId", 1));
        String firstResponse = mvc.perform(post("/api/me/playback-events").session(firstSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(playbackJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", is(clientEventId.toString())))
                .andExpect(jsonPath("$.dramaId", is(1)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/me/playback-events").session(firstSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(playbackJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startedAt", is(mapper.readTree(firstResponse).get("startedAt").asText())));
        mvc.perform(post("/api/me/playback-events").session(secondSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(playbackJson))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/me/playback-events").session(firstSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("eventId", clientEventId, "episodeId", 3))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/me/playback-events").session(secondSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("eventId", UUID.randomUUID(), "episodeId", 2))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/playback-events").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("eventId", UUID.randomUUID(), "episodeId", 1))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/playback-events").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(playbackJson))
                .andExpect(status().isUnauthorized());

        Episode episode = episodes.findById(1L).orElseThrow();
        savePlayback(UUID.randomUUID(), first, episode, fromStart.minusSeconds(1));
        savePlayback(UUID.randomUUID(), first, episode, fromStart);
        savePlayback(UUID.randomUUID(), second, episode, fromStart.plusSeconds(12 * 3600));
        savePlayback(UUID.randomUUID(), second, episode, endExclusive);

        savePaidOrder("DASH_PAY_WECHAT", first, PaymentProvider.WECHAT, 1990, fromStart);
        savePaidOrder("DASH_PAY_ALIPAY", second, PaymentProvider.ALIPAY, 290, todayStart.plusSeconds(3600));
        savePaidOrder("DASH_PAY_OUTSIDE", first, PaymentProvider.WECHAT, 500, endExclusive);
        savePaidOrder("DASH_PAY_ADMIN", admin, PaymentProvider.WECHAT, 9999, todayStart.plusSeconds(7200));

        saveReward("DASH_REWARD_ATTEMPT", first, episode, RewardPlatform.ANDROID,
                fromStart.plusSeconds(100), null);
        saveReward("DASH_REWARD_GRANTED", second, episode, RewardPlatform.ANDROID,
                fromStart.minusSeconds(1), todayStart.plusSeconds(100));
        saveReward("DASH_REWARD_ADMIN", admin, episode, RewardPlatform.IOS,
                todayStart.plusSeconds(100), todayStart.plusSeconds(200));

        mvc.perform(post("/api/dramas/1/interactions").session(firstSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"COMMENT\",\"text\":\"Dashboard comment\",\"episodeId\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dramas/1/interactions").session(secondSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DANMAKU\",\"text\":\"Dashboard\",\"episodeId\":1,\"at\":1,\"lane\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/me/favorites/1").session(firstSession).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard").session(firstSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").session(adminSession)
                        .param("from", from.toString()).param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", is(from.toString())))
                .andExpect(jsonPath("$.to", is(today.toString())))
                .andExpect(jsonPath("$.timezone", is("Asia/Shanghai")))
                .andExpect(jsonPath("$.totals.users", is(2)))
                .andExpect(jsonPath("$.totals.activeMembers", is(1)))
                .andExpect(jsonPath("$.totals.dramas", is(4)))
                .andExpect(jsonPath("$.totals.publishedDramas", is(3)))
                .andExpect(jsonPath("$.totals.episodes", is(8)))
                .andExpect(jsonPath("$.summary.newUsers", is(1)))
                .andExpect(jsonPath("$.summary.plays", is(3)))
                .andExpect(jsonPath("$.summary.viewers", is(2)))
                .andExpect(jsonPath("$.summary.paidOrders", is(2)))
                .andExpect(jsonPath("$.summary.revenueCents", is(2280)))
                .andExpect(jsonPath("$.summary.rewardAttempts", is(1)))
                .andExpect(jsonPath("$.summary.rewardGranted", is(1)))
                .andExpect(jsonPath("$.summary.comments", is(1)))
                .andExpect(jsonPath("$.summary.danmaku", is(1)))
                .andExpect(jsonPath("$.daily", hasSize(3)))
                .andExpect(jsonPath("$.daily[0].date", is(from.toString())))
                .andExpect(jsonPath("$.daily[0].newUsers", is(1)))
                .andExpect(jsonPath("$.daily[0].plays", is(2)))
                .andExpect(jsonPath("$.daily[0].viewers", is(2)))
                .andExpect(jsonPath("$.daily[0].paidOrders", is(1)))
                .andExpect(jsonPath("$.daily[0].revenueCents", is(1990)))
                .andExpect(jsonPath("$.daily[0].rewardAttempts", is(1)))
                .andExpect(jsonPath("$.daily[1].plays", is(0)))
                .andExpect(jsonPath("$.daily[1].paidOrders", is(0)))
                .andExpect(jsonPath("$.daily[2].plays", is(1)))
                .andExpect(jsonPath("$.daily[2].viewers", is(1)))
                .andExpect(jsonPath("$.daily[2].revenueCents", is(290)))
                .andExpect(jsonPath("$.daily[2].rewardGranted", is(1)))
                .andExpect(jsonPath("$.paymentChannels[0].provider", is("WECHAT")))
                .andExpect(jsonPath("$.paymentChannels[0].paidOrders", is(1)))
                .andExpect(jsonPath("$.paymentChannels[0].revenueCents", is(1990)))
                .andExpect(jsonPath("$.paymentChannels[1].provider", is("ALIPAY")))
                .andExpect(jsonPath("$.paymentChannels[1].revenueCents", is(290)))
                .andExpect(jsonPath("$.adPlatforms[0].platform", is("ANDROID")))
                .andExpect(jsonPath("$.adPlatforms[0].attempts", is(1)))
                .andExpect(jsonPath("$.adPlatforms[0].granted", is(1)))
                .andExpect(jsonPath("$.adPlatforms[1].attempts", is(0)))
                .andExpect(jsonPath("$.topDramas[0].id", is(1)))
                .andExpect(jsonPath("$.topDramas[0].plays", is(3)))
                .andExpect(jsonPath("$.topDramas[0].viewers", is(2)))
                .andExpect(jsonPath("$.topDramas[0].favorites", is(1)));

        mvc.perform(get("/api/admin/dashboard").session(adminSession)
                        .param("from", today.plusDays(1).toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard").session(adminSession)
                        .param("from", today.toString()).param("to", from.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard").session(adminSession)
                        .param("from", today.minusDays(93).toString()).param("to", today.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard").session(adminSession).param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", is(today.minusDays(6).toString())))
                .andExpect(jsonPath("$.to", is(today.toString())))
                .andExpect(jsonPath("$.daily", hasSize(7)));
    }

    private void savePlayback(UUID id, AppUser user, Episode episode, Instant startedAt) {
        PlaybackEvent event = new PlaybackEvent();
        event.setId(id.toString());
        event.setUser(user);
        event.setDrama(episode.getDrama());
        event.setEpisode(episode);
        event.setStartedAt(startedAt);
        playbackEvents.saveAndFlush(event);
    }

    private void savePaidOrder(String id, AppUser user, PaymentProvider provider, long amount, Instant paidAt) {
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(id);
        order.setUser(user);
        order.setProductCode(PaymentProduct.MEMBERSHIP);
        order.setMembershipDays(30);
        order.setProvider(provider);
        order.setStatus(PaymentStatus.PAID);
        order.setAmountFen(amount);
        order.setPaidAt(paidAt);
        paymentOrders.saveAndFlush(order);
    }

    private void saveReward(String id, AppUser user, Episode episode, RewardPlatform platform,
                            Instant createdAt, Instant grantedAt) {
        RewardAttempt attempt = new RewardAttempt();
        attempt.setId(id);
        attempt.setUser(user);
        attempt.setEpisode(episode);
        attempt.setPlatform(platform);
        attempt.setNonce("nonce-" + id);
        attempt.setStatus(grantedAt == null ? RewardAttemptStatus.PENDING : RewardAttemptStatus.GRANTED);
        attempt.setExpectedRewardName("dashboard");
        attempt.setExpectedRewardAmount(1);
        attempt.setCreatedAt(createdAt);
        attempt.setExpiresAt(createdAt.plusSeconds(600));
        attempt.setGrantedAt(grantedAt);
        rewardAttempts.saveAndFlush(attempt);
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "Password123!", "nickname", username))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
}
