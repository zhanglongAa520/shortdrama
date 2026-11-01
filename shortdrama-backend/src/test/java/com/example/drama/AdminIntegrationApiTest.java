package com.example.drama;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alipay.api.internal.util.AlipaySignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:integrationtest;DB_CLOSE_DELAY=-1",
        "app.admin-password=AdminTest123!",
        "app.rewards.csj.enabled=false"
})
@AutoConfigureMockMvc
class AdminIntegrationApiTest {
    private static final Path KEY_FILE = Path.of(System.getProperty("java.io.tmpdir"),
            "drama-integration-" + UUID.randomUUID(), "integrations.key");

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("integration.master-key-file", KEY_FILE::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired IntegrationConfigRepository configs;
    @Autowired PaymentGatewayService paymentGateway;

    @Test
    void adminApiEncryptsPersistsAndMasksSecrets() throws Exception {
        MockHttpSession user = register("integration_reader");
        MockHttpSession admin = login("admin", "AdminTest123!");
        mvc.perform(get("/api/admin/integrations/WECHAT").session(user))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/admin/integrations/WECHAT").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "values", Map.of("appId", "wx-incomplete")))))
                .andExpect(status().isBadRequest());

        Map<String, String> values = new LinkedHashMap<>();
        values.put("appId", "wx-app-1");
        values.put("mchId", "1900000001");
        values.put("merchantSerialNo", "SERIAL001");
        values.put("apiV3Key", "12345678901234567890123456789012");
        values.put("privateKey", "-----BEGIN PRIVATE KEY-----\nTEST_ONLY\n-----END PRIVATE KEY-----");
        values.put("notifyUrl", "https://merchant.example.test/api/payments/callbacks/wechat");
        mvc.perform(put("/api/admin/integrations/WECHAT").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "values", values))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(true)))
                .andExpect(jsonPath("$.values.appId", is("wx-app-1")))
                .andExpect(jsonPath("$.values.apiV3Key").doesNotExist())
                .andExpect(jsonPath("$.values.privateKey").doesNotExist())
                .andExpect(jsonPath("$.secretFieldsSet", hasItem("apiV3Key")))
                .andExpect(jsonPath("$.secretFieldsSet", hasItem("privateKey")));

        IntegrationConfig stored = configs.findById(IntegrationProvider.WECHAT).orElseThrow();
        assertFalse(stored.getEncryptedValues().contains("12345678901234567890123456789012"));
        assertFalse(stored.getEncryptedValues().contains("TEST_ONLY"));
        assertTrue(Files.isRegularFile(KEY_FILE));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(KEY_FILE));

        mvc.perform(put("/api/admin/integrations/WECHAT").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "values", Map.of(
                                "appId", "wx-app-2", "apiV3Key", "", "privateKey", "")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(true)))
                .andExpect(jsonPath("$.values.appId", is("wx-app-2")))
                .andExpect(jsonPath("$.secretFieldsSet", hasItem("privateKey")));

        mvc.perform(put("/api/admin/integrations/WECHAT").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "values", Map.of(),
                                "clearSecrets", java.util.List.of("privateKey")))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/integrations/WECHAT").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false, "values", Map.of(),
                                "clearSecrets", java.util.List.of("privateKey")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.configured", is(false)));
    }

    @Test
    void alipaySavedConfigurationIsUsedImmediatelyForOfflineVerification() throws Exception {
        MockHttpSession admin = login("admin", "AdminTest123!");
        MockHttpSession user = register("dynamic_alipay_user");
        KeyPair keyPair = rsaKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String publicPem = "-----BEGIN PUBLIC KEY-----\n" + publicKey + "\n-----END PUBLIC KEY-----";

        mvc.perform(put("/api/admin/integrations/ALIPAY").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false, "values", Map.of("gateway", "https://evil.test")))))
                .andExpect(status().isBadRequest());

        Map<String, String> values = new LinkedHashMap<>();
        values.put("appId", "2026000000001001");
        values.put("sellerId", "2088000000001001");
        values.put("privateKey", "-----BEGIN PRIVATE KEY-----\n" + privateKey + "\n-----END PRIVATE KEY-----");
        values.put("publicKey", publicPem);
        values.put("notifyUrl", "https://merchant.example.test/api/payments/callbacks/alipay");
        values.put("gateway", "SANDBOX");
        mvc.perform(put("/api/admin/integrations/ALIPAY").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "values", values))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured", is(true)));
        mvc.perform(get("/api/payments/config").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.ALIPAY.configured", is(true)));

        Map<String, String> notice = new LinkedHashMap<>();
        notice.put("app_id", "2026000000001001");
        notice.put("seller_id", "2088000000001001");
        notice.put("trade_status", "TRADE_SUCCESS");
        notice.put("total_amount", "19.90");
        notice.put("out_trade_no", "DYNAMIC_ORDER_1");
        notice.put("trade_no", "DYNAMIC_TRADE_1");
        notice.put("sign", AlipaySignature.sign(notice, privateKey, "UTF-8", "RSA2"));
        notice.put("sign_type", "RSA2");
        PaymentGatewayService.VerifiedPayment verified = paymentGateway.verifyAlipay(notice);
        assertEquals("DYNAMIC_ORDER_1", verified.orderNo());
        assertEquals(1990, verified.amountFen());
    }

    @Test
    void csjAdminConfigurationOverridesDisabledEnvironmentAndUnlocksEpisode() throws Exception {
        MockHttpSession admin = login("admin", "AdminTest123!");
        MockHttpSession user = register("dynamic_csj_user");
        String securityKey = "dynamic-csj-security-key";
        Map<String, Object> values = Map.of(
                "appId", "csj-app-1",
                "placementId", "csj-placement-1",
                "securityKey", securityKey,
                "rewardName", "解锁本集",
                "rewardAmount", 1);
        mvc.perform(put("/api/admin/integrations/CSJ_ANDROID").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "values", values))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured", is(true)));
        mvc.perform(get("/api/rewards/csj/config").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.platforms.ANDROID.enabled", is(true)))
                .andExpect(jsonPath("$.platforms.ANDROID.appId", is("csj-app-1")));

        JsonNode attempt = mapper.readTree(mvc.perform(post("/api/rewards/csj/attempts").session(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"episodeId\":2,\"platform\":\"ANDROID\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String transactionId = "CSJ_DYNAMIC_TX_1";
        String signature = CsJRewardService.signature(securityKey, transactionId);
        mvc.perform(get("/api/rewards/csj/callback")
                        .param("user_id", attempt.get("providerUserId").asText())
                        .param("trans_id", transactionId)
                        .param("reward_name", "解锁本集")
                        .param("reward_amount", "1")
                        .param("extra", attempt.get("extra").asText())
                        .param("sign", signature))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isValid", is(true)));
        mvc.perform(get("/api/dramas/1").session(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.episodes[1].locked", is(false)));
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

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
