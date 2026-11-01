package com.example.drama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentGatewayServiceAlipayTest {
    private static final String APP_ID = "2026000000000001";
    private static final String SELLER_ID = "2088000000000001";
    private static String privateKey;
    private static PaymentGatewayService gateway;

    @BeforeAll
    static void setUpGateway() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + publicKey + "\n-----END PUBLIC KEY-----";
        IntegrationConfigService integrations = mock(IntegrationConfigService.class);
        IntegrationConfigService.EffectiveConfig config = new IntegrationConfigService.EffectiveConfig(
                IntegrationProvider.ALIPAY, true, 1, Map.of(
                "appId", APP_ID,
                "sellerId", SELLER_ID,
                "privateKey", privateKey,
                "publicKey", publicKeyPem,
                "notifyUrl", "https://merchant.example.test/api/payments/callbacks/alipay",
                "gateway", IntegrationConfigService.ALIPAY_PRODUCTION));
        when(integrations.effective(IntegrationProvider.ALIPAY)).thenReturn(config);
        when(integrations.isConfigured(config)).thenReturn(true);
        gateway = new PaymentGatewayService(new ObjectMapper(), integrations);
    }

    @Test
    void verifiesOfficialSdkSignedNotificationOffline() throws Exception {
        PaymentGatewayService.VerifiedPayment verified = gateway.verifyAlipay(signedNotice(Map.of()));

        assertEquals("ORDER_20261101_001", verified.orderNo());
        assertEquals("ALI_TRADE_001", verified.providerTradeNo());
        assertEquals(1990, verified.amountFen());
    }

    @Test
    void rejectsForgedSignature() throws Exception {
        Map<String, String> notice = signedNotice(Map.of());
        String signature = notice.get("sign");
        notice.put("sign", (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1));

        assertBadRequest(notice);
    }

    @Test
    void rejectsSignedNotificationForWrongAppOrSeller() throws Exception {
        assertBadRequest(signedNotice(Map.of("app_id", "2026000000000999")));
        assertBadRequest(signedNotice(Map.of("seller_id", "2088000000000999")));
    }

    @Test
    void rejectsSignedNotificationWithInvalidAmountOrTradeState() throws Exception {
        assertBadRequest(signedNotice(Map.of("total_amount", "19.999")));
        assertBadRequest(signedNotice(Map.of("trade_status", "WAIT_BUYER_PAY")));
    }

    private static Map<String, String> signedNotice(Map<String, String> overrides) throws AlipayApiException {
        Map<String, String> notice = new LinkedHashMap<>();
        notice.put("app_id", APP_ID);
        notice.put("seller_id", SELLER_ID);
        notice.put("trade_status", "TRADE_SUCCESS");
        notice.put("total_amount", "19.90");
        notice.put("out_trade_no", "ORDER_20261101_001");
        notice.put("trade_no", "ALI_TRADE_001");
        notice.putAll(overrides);
        notice.put("sign", AlipaySignature.sign(notice, privateKey, "UTF-8", "RSA2"));
        notice.put("sign_type", "RSA2");
        return notice;
    }

    private static void assertBadRequest(Map<String, String> notice) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gateway.verifyAlipay(notice));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
