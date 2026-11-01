package com.example.drama;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dramatest;DB_CLOSE_DELAY=-1",
        "app.admin-password=AdminTest123!",
        "app.media-root=./media/demo"
})
class DramaApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PaymentService paymentService;
    @Autowired PaymentOrderRepository paymentOrders;
    @Autowired AppUserRepository userRepository;
    @Autowired EpisodeRepository episodeRepository;
    @Autowired EpisodeUnlockRepository unlockRepository;

    @Test
    void csrfRegistrationSessionAndRoleProtectionWork() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName", is("X-CSRF-TOKEN")))
                .andReturn();
        MockHttpSession preAuth = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String oldSessionId = preAuth.getId();
        String oldToken = mapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get("/api/dramas")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("请先登录")));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("no_csrf_user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("无权访问该资源")));

        MvcResult registration = mvc.perform(post("/api/auth/register").session(preAuth)
                        .header("X-CSRF-TOKEN", oldToken).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("auth_user")))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession user = (MockHttpSession) registration.getRequest().getSession(false);
        assertNotEquals(oldSessionId, user.getId());
        MvcResult refreshedCsrf = mvc.perform(get("/api/auth/csrf").session(user))
                .andExpect(status().isOk()).andReturn();
        String refreshedToken = mapper.readTree(refreshedCsrf.getResponse().getContentAsString()).get("token").asText();
        assertNotEquals(oldToken, refreshedToken);
        mvc.perform(get("/api/auth/me").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("auth_user")))
                .andExpect(jsonPath("$.role", is("USER")));
        mvc.perform(get("/api/admin/overview").session(user)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("无权访问该资源")));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("admin")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"role_user\",\"password\":\"Password123!\",\"nickname\":\"角色用户\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("USER")));
        mvc.perform(post("/api/auth/logout").session(user).header("X-CSRF-TOKEN", refreshedToken))
                .andExpect(status().isNoContent());
        assertTrue(user.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        MvcResult loginCsrf = mvc.perform(get("/api/auth/csrf")).andReturn();
        MockHttpSession beforeLogin = (MockHttpSession) loginCsrf.getRequest().getSession(false);
        String beforeLoginId = beforeLogin.getId();
        String beforeLoginToken = mapper.readTree(loginCsrf.getResponse().getContentAsString()).get("token").asText();
        MvcResult login = mvc.perform(post("/api/auth/login").session(beforeLogin)
                        .header("X-CSRF-TOKEN", beforeLoginToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"AdminTest123!\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession adminSession = (MockHttpSession) login.getRequest().getSession(false);
        assertNotEquals(beforeLoginId, adminSession.getId());
        MvcResult loginFreshCsrf = mvc.perform(get("/api/auth/csrf").session(adminSession)).andReturn();
        assertNotEquals(beforeLoginToken,
                mapper.readTree(loginFreshCsrf.getResponse().getContentAsString()).get("token").asText());
    }

    @Test
    void adminCanReadCatalogAndPublishedDramaNeedsAnEpisode() throws Exception {
        MockHttpSession admin = login("admin", "AdminTest123!");
        mvc.perform(get("/api/dramas").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].episodes[0].videoUrl", is("/api/media/episodes/1")));
        mvc.perform(get("/api/admin/overview").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDramas", is(4)))
                .andExpect(jsonPath("$.publishedDramas", is(3)))
                .andExpect(jsonPath("$.totalEpisodes", is(8)));

        String emptyPublished = """
                {"title":"无集数短剧","description":"简介","genre":"悬疑",
                 "coverUrl":"/posters/test.svg","status":"PUBLISHED","episodes":[]}
                """;
        mvc.perform(post("/api/admin/dramas").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(emptyPublished))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/dramas/1").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(emptyPublished))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dramas/1").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.episodes", hasSize(2)));
        mvc.perform(post("/api/admin/dramas").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"外链付费剧","genre":"测试","coverUrl":"/poster.svg","status":"DRAFT",
                                 "episodes":[{"title":"锁集","videoUrl":"https://example.com/video.mp4","durationSeconds":10,"free":false}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void interactionsAreValidatedSharedAndModerated() throws Exception {
        MockHttpSession user = register("interaction_user");
        String created = mvc.perform(post("/api/dramas/1/interactions").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"COMMENT\",\"text\":\"这段剧情很好看\",\"episodeId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("VISIBLE")))
                .andExpect(jsonPath("$.username", is("interaction_user")))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        mvc.perform(post("/api/dramas/1/interactions").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DANMAKU\",\"text\":\"高能！\",\"episodeId\":1,\"at\":3.5,\"lane\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.at", is(3.5)))
                .andExpect(jsonPath("$.lane", is(2)));
        mvc.perform(post("/api/dramas/1/interactions").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DANMAKU\",\"text\":\"错集\",\"episodeId\":3,\"at\":1,\"lane\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/dramas/1/interactions").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DANMAKU\",\"text\":\"错误轨道\",\"episodeId\":1,\"at\":1,\"lane\":3}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/dramas/1/interactions").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DANMAKU\",\"text\":\"%s\",\"episodeId\":1,\"at\":1,\"lane\":0}"
                                .formatted("长".repeat(31))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dramas/1/interactions?type=COMMENT").session(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        MockHttpSession admin = login("admin", "AdminTest123!");
        mvc.perform(patch("/api/admin/interactions/{id}", id).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("HIDDEN")));
        mvc.perform(get("/api/dramas/1/interactions?type=COMMENT").session(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/admin/interactions?type=COMMENT&status=HIDDEN").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id", is((int) id)));
        mvc.perform(patch("/api/admin/interactions/{id}", id).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VISIBLE\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/interactions/{id}", id).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void protectedMediaSupportsByteRangesAndWhitelist() throws Exception {
        MockHttpSession user = register("media_user");
        mvc.perform(get("/api/media/demo/drama-1.mp4")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/media/demo/drama-1.mp4").session(user).header(HttpHeaders.RANGE, "bytes=0-99"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/media/episodes/1").session(user).header(HttpHeaders.RANGE, "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"));
        mvc.perform(get("/api/media/episodes/2").session(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/media/demo/not-allowed.mp4").session(user)).andExpect(status().isForbidden());
    }

    @Test
    void adminCrudPreservesEpisodeIdentityAndCleansRemovedInteractions() throws Exception {
        MockHttpSession admin = login("admin", "AdminTest123!");
        String draft = """
                {"title":"CRUD测试剧","description":"简介","genre":"悬疑","coverUrl":"/posters/test.svg",
                 "status":"DRAFT","episodes":[
                   {"title":"第一集","videoUrl":"/api/media/demo/drama-1.mp4","durationSeconds":20,"free":true},
                   {"title":"第二集","videoUrl":"/api/media/demo/drama-2.mp4","durationSeconds":25,"free":false}]}
                """;
        String created = mvc.perform(post("/api/admin/dramas").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(draft))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.episodes", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        JsonNode drama = mapper.readTree(created);
        long dramaId = drama.get("id").asLong();
        long firstEpisodeId = drama.get("episodes").get(0).get("id").asLong();
        long secondEpisodeId = drama.get("episodes").get(1).get("id").asLong();

        String wrongOwnership = publishedUpdate(1, secondEpisodeId);
        mvc.perform(put("/api/admin/dramas/{id}", dramaId).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(wrongOwnership))
                .andExpect(status().isBadRequest());

        String published = publishedUpdate(secondEpisodeId, firstEpisodeId);
        mvc.perform(put("/api/admin/dramas/{id}", dramaId).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(published))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[0].id", is((int) secondEpisodeId)))
                .andExpect(jsonPath("$.episodes[1].id", is((int) firstEpisodeId)));
        mvc.perform(get("/api/dramas/{id}", dramaId).session(admin)).andExpect(status().isOk());

        MockHttpSession user = register("crud_user");
        String interactionJson = mvc.perform(post("/api/dramas/{id}/interactions", dramaId).session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"COMMENT\",\"text\":\"即将随分集删除\",\"episodeId\":%d}"
                                .formatted(firstEpisodeId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long interactionId = mapper.readTree(interactionJson).get("id").asLong();

        String removeFirstEpisode = """
                {"title":"CRUD测试剧","description":"已更新","genre":"悬疑","coverUrl":"/posters/test.svg",
                 "status":"PUBLISHED","episodes":[
                   {"id":%d,"title":"第二集","videoUrl":"/api/media/demo/drama-2.mp4","durationSeconds":25,"free":false}]}
                """.formatted(secondEpisodeId);
        mvc.perform(put("/api/admin/dramas/{id}", dramaId).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(removeFirstEpisode))
                .andExpect(status().isOk()).andExpect(jsonPath("$.episodes", hasSize(1)));
        JsonNode allInteractions = mapper.readTree(mvc.perform(get("/api/admin/interactions").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertFalse(allInteractions.findValuesAsText("id").contains(Long.toString(interactionId)));

        mvc.perform(delete("/api/admin/dramas/{id}", dramaId).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/dramas/{id}", dramaId).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void favoritesHistoryMembershipAndEpisodeUnlockAreServerEnforced() throws Exception {
        MockHttpSession user = register("rights_user");
        mvc.perform(get("/api/dramas/1").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[0].locked", is(false)))
                .andExpect(jsonPath("$.episodes[0].videoUrl", is("/api/media/episodes/1")))
                .andExpect(jsonPath("$.episodes[1].locked", is(true)))
                .andExpect(jsonPath("$.episodes[1].videoUrl", nullValue()));
        mvc.perform(post("/api/dramas/1/episodes/2/playback").session(user).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/me/history/2").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"progressSeconds\":2.5}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/me/favorites/1").session(user).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/me/favorites/1").session(user).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/me/favorites").session(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        MockHttpSession admin = login("admin", "AdminTest123!");
        long userId = findUserId(admin, "rights_user");
        mvc.perform(post("/api/admin/users/{userId}/episode-unlocks/2", userId).session(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.episodeId", is(2)));
        mvc.perform(post("/api/admin/users/{userId}/episode-unlocks/2", userId).session(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/entitlements").session(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unlockedEpisodeIds", hasItem(2)));
        mvc.perform(get("/api/media/episodes/2").session(user).header(HttpHeaders.RANGE, "bytes=0-31"))
                .andExpect(status().isPartialContent());

        mvc.perform(post("/api/dramas/1/episodes/2/playback").session(user).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumeAtSeconds", is(0.0)))
                .andExpect(jsonPath("$.nextEpisodeId", nullValue()));
        mvc.perform(post("/api/dramas/1/episodes/2/playback").session(user).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(put("/api/me/history/2").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"progressSeconds\":6.5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dramaTitle", is("时光来信")));
        mvc.perform(put("/api/me/history/2").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"progressSeconds\":2.25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.progressSeconds", is(2.25)));
        mvc.perform(get("/api/me/history").session(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].coverUrl", is("/posters/after-rain.svg")));
        mvc.perform(put("/api/me/history/2").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"progressSeconds\":99}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/users/{userId}/episode-unlocks/2", userId).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/media/episodes/2").session(user)).andExpect(status().isForbidden());

        String expiresAt = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        mvc.perform(post("/api/admin/users/{userId}/membership", userId).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"%s\"}".formatted(expiresAt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.membershipActive", is(true)));
        mvc.perform(get("/api/media/episodes/4").session(user)).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/users/{userId}/membership", userId).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/media/episodes/4").session(user)).andExpect(status().isForbidden());

        mvc.perform(delete("/api/me/history/1").session(user).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/history").session(user)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/me/favorites/1").session(user).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/favorites").session(user)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void recommendationsCountUniqueViewsAndPaymentRequiresConfiguration() throws Exception {
        MockHttpSession admin = login("admin", "AdminTest123!");
        mvc.perform(patch("/api/admin/dramas/1/recommendation").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true,\"recommendationRank\":2}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/dramas/2/recommendation").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true,\"recommendationRank\":1}"))
                .andExpect(status().isOk());
        MockHttpSession viewer = register("ranking_user");
        JsonNode beforeRecommendations = mapper.readTree(mvc.perform(get("/api/dramas/recommendations").session(viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long beforeViews = 0;
        for (JsonNode drama : beforeRecommendations.get("hot")) {
            if (drama.get("id").asLong() == 1) beforeViews = drama.get("viewCount").asLong();
        }
        mvc.perform(post("/api/dramas/1/episodes/1/playback").session(viewer).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/dramas/1/episodes/1/playback").session(viewer).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/dramas/recommendations").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured[0].id", is(2)))
                .andExpect(jsonPath("$.featured[1].id", is(1)))
                .andExpect(jsonPath("$.hot[0].id", is(1)))
                .andExpect(jsonPath("$.hot[0].viewCount", is((int) beforeViews + 1)));

        mvc.perform(get("/api/payments/config").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.WECHAT.configured", is(false)))
                .andExpect(jsonPath("$.membership.priceCents", is(1990)));
        mvc.perform(post("/api/payments/orders").session(viewer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"WECHAT\",\"productType\":\"MEMBERSHIP\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/payments/orders").session(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/payments/callbacks/alipay")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("out_trade_no", "invalid"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void verifiedPaymentsGrantRightsExactlyOnceAndSerializeMembershipExtension() throws Exception {
        register("payment_user");
        AppUser user = userRepository.findByUsernameIgnoreCase("payment_user").orElseThrow();
        PaymentOrder first = membershipOrder("PAY_TEST_1", user, PaymentProvider.WECHAT);
        PaymentOrder second = membershipOrder("PAY_TEST_2", user, PaymentProvider.ALIPAY);
        paymentOrders.saveAllAndFlush(java.util.List.of(first, second));

        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> paymentService.complete(PaymentProvider.WECHAT,
                    new PaymentGatewayService.VerifiedPayment("PAY_TEST_1", "WX_TX_1", 1990)));
            var two = pool.submit(() -> paymentService.complete(PaymentProvider.ALIPAY,
                    new PaymentGatewayService.VerifiedPayment("PAY_TEST_2", "ALI_TX_2", 1990)));
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        Instant expiry = userRepository.findById(user.getId()).orElseThrow().getMembershipExpiresAt();
        assertTrue(expiry.isAfter(Instant.now().plus(59, ChronoUnit.DAYS)));
        paymentService.complete(PaymentProvider.WECHAT,
                new PaymentGatewayService.VerifiedPayment("PAY_TEST_1", "WX_TX_1", 1990));
        assertEquals(expiry, userRepository.findById(user.getId()).orElseThrow().getMembershipExpiresAt());

        PaymentOrder episodeOrder = new PaymentOrder();
        episodeOrder.setOrderNo("PAY_TEST_EPISODE");
        episodeOrder.setUser(user);
        episodeOrder.setProductCode(PaymentProduct.EPISODE);
        episodeOrder.setProvider(PaymentProvider.ALIPAY);
        episodeOrder.setStatus(PaymentStatus.CREATED);
        episodeOrder.setAmountFen(290);
        episodeOrder.setEpisode(episodeRepository.findById(2L).orElseThrow());
        paymentOrders.saveAndFlush(episodeOrder);
        paymentService.complete(PaymentProvider.ALIPAY,
                new PaymentGatewayService.VerifiedPayment("PAY_TEST_EPISODE", "ALI_TX_EP", 290));
        paymentService.complete(PaymentProvider.ALIPAY,
                new PaymentGatewayService.VerifiedPayment("PAY_TEST_EPISODE", "ALI_TX_EP", 290));
        assertEquals(1L, unlockRepository.findByUserIdOrderByGrantedAtDesc(user.getId()).stream()
                .filter(unlock -> unlock.getEpisode().getId().equals(2L)).count());

        PaymentOrder mismatch = membershipOrder("PAY_TEST_MISMATCH", user, PaymentProvider.WECHAT);
        paymentOrders.saveAndFlush(mismatch);
        assertThrows(ResponseStatusException.class, () -> paymentService.complete(PaymentProvider.WECHAT,
                new PaymentGatewayService.VerifiedPayment("PAY_TEST_MISMATCH", "WX_BAD", 1)));
        assertEquals(PaymentStatus.CREATED, paymentOrders.findById("PAY_TEST_MISMATCH").orElseThrow().getStatus());
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registerJson(username)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String registerJson(String username) {
        return """
                {"username":"%s","password":"Password123!","nickname":"测试用户"}
                """.formatted(username);
    }

    private String publishedUpdate(long firstId, long secondId) {
        return """
                {"title":"CRUD测试剧","description":"已发布","genre":"悬疑","coverUrl":"/posters/test.svg",
                 "status":"PUBLISHED","episodes":[
                   {"id":%d,"title":"第二集","videoUrl":"/api/media/demo/drama-2.mp4","durationSeconds":25,"free":false},
                   {"id":%d,"title":"第一集","videoUrl":"/api/media/demo/drama-1.mp4","durationSeconds":20,"free":true}]}
                """.formatted(firstId, secondId);
    }

    private long findUserId(MockHttpSession admin, String username) throws Exception {
        JsonNode users = mapper.readTree(mvc.perform(get("/api/admin/users").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode user : users) if (username.equals(user.get("username").asText())) return user.get("id").asLong();
        throw new AssertionError("User not found: " + username);
    }

    private PaymentOrder membershipOrder(String orderNo, AppUser user, PaymentProvider provider) {
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo);
        order.setUser(user);
        order.setProductCode(PaymentProduct.MEMBERSHIP);
        order.setProvider(provider);
        order.setStatus(PaymentStatus.CREATED);
        order.setAmountFen(1990);
        order.setMembershipDays(30);
        return order;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
