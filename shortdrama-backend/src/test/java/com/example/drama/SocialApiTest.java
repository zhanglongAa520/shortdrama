package com.example.drama;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:socialtest;DB_CLOSE_DELAY=-1",
        "app.admin-password=AdminTest123!"
})
class SocialApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SocialService socialService;
    @MockitoSpyBean SocialCountCache socialCache;
    @MockitoSpyBean SocialCountDatabaseReader countReader;

    @Test
    void socialCountsPersistedVisibleDataAndLikesAreIdempotent() throws Exception {
        MockHttpSession admin = login("admin", "AdminTest123!");
        JsonNode published = createDrama(admin, "PUBLISHED");
        long dramaId = published.get("id").asLong();
        long episodeId = published.get("episodes").get(0).get("id").asLong();
        long draftId = createDrama(admin, "DRAFT").get("id").asLong();
        MockHttpSession user = register("social_user");

        mvc.perform(get("/api/dramas/{id}/social", dramaId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dramas/{id}/social", draftId).session(admin)).andExpect(status().isNotFound());
        mvc.perform(put("/api/me/likes/{id}", draftId).session(admin).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/dramas/{id}/social", dramaId).session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dramaId", is((int) dramaId)))
                .andExpect(jsonPath("$.likeCount", is(0)))
                .andExpect(jsonPath("$.commentCount", is(0)))
                .andExpect(jsonPath("$.favoriteCount", is(0)))
                .andExpect(jsonPath("$.liked", is(false)))
                .andExpect(jsonPath("$.favorited", is(false)));

        createInteraction(user, dramaId, episodeId, "COMMENT", "公开评论", null);
        long hiddenId = createInteraction(user, dramaId, episodeId, "COMMENT", "隐藏评论", null);
        createInteraction(user, dramaId, episodeId, "DANMAKU", "这是一条弹幕", "\"at\":1,\"lane\":0,");
        mvc.perform(patch("/api/admin/interactions/{id}", hiddenId).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk());
        verify(socialCache, times(3)).invalidateAfterCommit(dramaId);
        clearInvocations(socialCache);

        mvc.perform(post("/api/me/favorites/{id}", dramaId).session(user).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(put("/api/me/likes/{id}", dramaId).session(user).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount", is(1)))
                .andExpect(jsonPath("$.liked", is(true)));
        mvc.perform(put("/api/me/likes/{id}", dramaId).session(user).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount", is(1)));

        mvc.perform(post("/api/me/favorites/{id}", dramaId).session(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(put("/api/me/likes/{id}", dramaId).session(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount", is(2)))
                .andExpect(jsonPath("$.liked", is(true))).andExpect(jsonPath("$.favorited", is(true)));
        mvc.perform(get("/api/dramas/{id}/social", dramaId).session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount", is(2)))
                .andExpect(jsonPath("$.commentCount", is(1)))
                .andExpect(jsonPath("$.favoriteCount", is(2)))
                .andExpect(jsonPath("$.liked", is(true)))
                .andExpect(jsonPath("$.favorited", is(true)));
        verify(socialCache, times(4)).invalidateAfterCommit(dramaId);
        clearInvocations(socialCache);

        mvc.perform(delete("/api/me/likes/{id}", dramaId).session(user).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount", is(1)))
                .andExpect(jsonPath("$.liked", is(false)));
        mvc.perform(delete("/api/me/likes/{id}", dramaId).session(user).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount", is(1)));
        verify(socialCache, times(1)).invalidateAfterCommit(dramaId);
        clearInvocations(socialCache);

        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> socialService.like("social_user", dramaId));
            var second = pool.submit(() -> socialService.like("social_user", dramaId));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        mvc.perform(get("/api/dramas/{id}/social", dramaId).session(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount", is(2)));
        verify(socialCache, times(1)).invalidateAfterCommit(dramaId);
        clearInvocations(socialCache);

        mvc.perform(delete("/api/admin/dramas/{id}", dramaId).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
        verify(socialCache, times(1)).invalidateAfterCommit(dramaId);
        mvc.perform(get("/api/dramas/{id}/social", dramaId).session(user)).andExpect(status().isNotFound());
        verify(countReader, atLeastOnce()).loadCommitted(dramaId);
        verify(countReader, atLeastOnce()).loadCurrentTransaction(dramaId);
    }

    private JsonNode createDrama(MockHttpSession admin, String statusValue) throws Exception {
        String episodes = "PUBLISHED".equals(statusValue)
                ? "[{\"title\":\"第一集\",\"videoUrl\":\"https://example.com/free.mp4\",\"durationSeconds\":30,\"free\":true}]"
                : "[]";
        String response = mvc.perform(post("/api/admin/dramas").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"社交统计测试剧-%s","description":"测试","genre":"测试",
                                 "coverUrl":"/posters/social.svg","status":"%s","episodes":%s}
                                """.formatted(statusValue, statusValue, episodes)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private long createInteraction(MockHttpSession session, long dramaId, long episodeId,
                                   String type, String text, String extraJson) throws Exception {
        String extra = extraJson == null ? "" : extraJson;
        String response = mvc.perform(post("/api/dramas/{id}/interactions", dramaId)
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{%s\"type\":\"%s\",\"text\":\"%s\",\"episodeId\":%d}"
                                .formatted(extra, type, text, episodeId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").asLong();
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Password123!\",\"nickname\":\"测试用户\"}"
                                .formatted(username)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(username, password)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
