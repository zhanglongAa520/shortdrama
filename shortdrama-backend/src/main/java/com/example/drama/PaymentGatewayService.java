package com.example.drama;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.Amount;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.h5.model.PrepayRequest;
import com.wechat.pay.java.service.payments.h5.model.PrepayResponse;
import com.wechat.pay.java.service.payments.h5.model.SceneInfo;
import com.wechat.pay.java.service.payments.model.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentGatewayService {
    public record GatewayResult(String paymentUrl, String html) {}
    public record VerifiedPayment(String orderNo, String providerTradeNo, long amountFen) {}

    private final ObjectMapper objectMapper;
    private final IntegrationConfigService integrations;
    private volatile WechatConfigCache cachedWechatConfig;

    private record WechatConfigCache(long version, int fingerprint, RSAAutoCertificateConfig config) {}

    public PaymentGatewayService(ObjectMapper objectMapper, IntegrationConfigService integrations) {
        this.objectMapper = objectMapper;
        this.integrations = integrations;
    }

    public boolean configured(PaymentProvider provider) {
        IntegrationConfigService.EffectiveConfig config = paymentConfig(provider);
        return config.enabled() && integrations.isConfigured(config);
    }

    public GatewayResult create(PaymentOrder order, String clientIp) {
        IntegrationConfigService.EffectiveConfig config = paymentConfig(order.getProvider());
        if (!config.enabled() || !integrations.isConfigured(config)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "支付渠道尚未配置");
        }
        try {
            return order.getProvider() == PaymentProvider.WECHAT
                    ? createWechat(order, clientIp, config) : createAlipay(order, config);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "支付渠道请求失败");
        }
    }

    public VerifiedPayment verifyWechat(String body, String serial, String timestamp,
                                         String nonce, String signature) {
        IntegrationConfigService.EffectiveConfig config = integrations.effective(IntegrationProvider.WECHAT);
        if (!config.enabled() || !integrations.isConfigured(config)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付尚未配置");
        }
        try {
            RequestParam request = new RequestParam.Builder().serialNumber(serial).timestamp(timestamp)
                    .nonce(nonce).signature(signature).body(body).build();
            Transaction transaction = new NotificationParser(wechatConfig(config)).parse(request, Transaction.class);
            if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS
                    || !config.value("appId").equals(transaction.getAppid())
                    || !config.value("mchId").equals(transaction.getMchid())
                    || transaction.getAmount() == null || transaction.getAmount().getTotal() == null
                    || !"CNY".equals(transaction.getAmount().getCurrency())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信支付回调数据不匹配");
            }
            return new VerifiedPayment(transaction.getOutTradeNo(), transaction.getTransactionId(),
                    transaction.getAmount().getTotal());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信支付回调验签失败");
        }
    }

    public VerifiedPayment verifyAlipay(Map<String, String> params) {
        IntegrationConfigService.EffectiveConfig config = integrations.effective(IntegrationProvider.ALIPAY);
        if (!config.enabled() || !integrations.isConfigured(config)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "支付宝尚未配置");
        }
        try {
            if (!AlipaySignature.rsaCheckV1(params, normalizeAlipayKey(config.value("publicKey")),
                    "UTF-8", "RSA2")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付宝回调验签失败");
            }
            String tradeStatus = params.get("trade_status");
            if (!("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))
                    || !config.value("appId").equals(params.get("app_id"))
                    || !config.value("sellerId").equals(params.get("seller_id"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付宝回调数据不匹配");
            }
            long amountFen = new BigDecimal(params.get("total_amount")).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return new VerifiedPayment(params.get("out_trade_no"), params.get("trade_no"), amountFen);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付宝回调验签失败");
        }
    }

    private GatewayResult createWechat(PaymentOrder order, String clientIp,
                                       IntegrationConfigService.EffectiveConfig config) {
        PrepayRequest request = new PrepayRequest();
        request.setAppid(config.value("appId"));
        request.setMchid(config.value("mchId"));
        request.setDescription(subject(order));
        request.setOutTradeNo(order.getOrderNo());
        request.setNotifyUrl(config.value("notifyUrl"));
        Amount amount = new Amount();
        amount.setTotal(Math.toIntExact(order.getAmountFen()));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        SceneInfo scene = new SceneInfo();
        scene.setPayerClientIp(clientIp);
        H5Info h5 = new H5Info();
        h5.setType("Wap");
        h5.setAppName("映刻短剧");
        h5.setAppUrl(order.getReturnUrl() == null ? config.value("notifyUrl") : order.getReturnUrl());
        scene.setH5Info(h5);
        request.setSceneInfo(scene);
        PrepayResponse response = new H5Service.Builder().config(wechatConfig(config)).build().prepay(request);
        String url = response.getH5Url();
        if (order.getReturnUrl() != null && !order.getReturnUrl().isBlank()) {
            url += (url.contains("?") ? "&" : "?") + "redirect_url="
                    + URLEncoder.encode(order.getReturnUrl(), StandardCharsets.UTF_8);
        }
        return new GatewayResult(url, null);
    }

    private GatewayResult createAlipay(PaymentOrder order, IntegrationConfigService.EffectiveConfig config)
            throws AlipayApiException, JsonProcessingException {
        AlipayClient client = new DefaultAlipayClient(alipayGateway(config.value("gateway")),
                config.value("appId"), normalizeAlipayKey(config.value("privateKey")),
                "json", "UTF-8", normalizeAlipayKey(config.value("publicKey")), "RSA2");
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(config.value("notifyUrl"));
        request.setReturnUrl(order.getReturnUrl());
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("out_trade_no", order.getOrderNo());
        content.put("total_amount", BigDecimal.valueOf(order.getAmountFen(), 2).toPlainString());
        content.put("subject", subject(order));
        content.put("product_code", "QUICK_WAP_WAY");
        content.put("timeout_express", "15m");
        request.setBizContent(objectMapper.writeValueAsString(content));
        AlipayTradeWapPayResponse response = client.pageExecute(request, "GET");
        if (!response.isSuccess()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "支付宝创建订单失败");
        return new GatewayResult(response.getBody(), null);
    }

    private RSAAutoCertificateConfig wechatConfig(IntegrationConfigService.EffectiveConfig settings) {
        int fingerprint = Objects.hash(settings.value("mchId"), settings.value("merchantSerialNo"),
                settings.value("apiV3Key"), settings.value("privateKey"));
        WechatConfigCache current = cachedWechatConfig;
        if (current != null && current.version() == settings.version() && current.fingerprint() == fingerprint) {
            return current.config();
        }
        synchronized (this) {
            current = cachedWechatConfig;
            if (current == null || current.version() != settings.version()
                    || current.fingerprint() != fingerprint) {
                RSAAutoCertificateConfig rebuilt = new RSAAutoCertificateConfig.Builder()
                        .merchantId(settings.value("mchId"))
                        .privateKey(settings.value("privateKey"))
                        .merchantSerialNumber(settings.value("merchantSerialNo"))
                        .apiV3Key(settings.value("apiV3Key")).build();
                cachedWechatConfig = new WechatConfigCache(settings.version(), fingerprint, rebuilt);
            }
            return cachedWechatConfig.config();
        }
    }

    private IntegrationConfigService.EffectiveConfig paymentConfig(PaymentProvider provider) {
        return integrations.effective(provider == PaymentProvider.WECHAT
                ? IntegrationProvider.WECHAT : IntegrationProvider.ALIPAY);
    }

    private String alipayGateway(String gateway) {
        return IntegrationConfigService.ALIPAY_SANDBOX.equals(gateway)
                ? IntegrationConfigService.ALIPAY_SANDBOX_URL : IntegrationConfigService.ALIPAY_PRODUCTION_URL;
    }

    static String normalizeAlipayKey(String value) {
        if (value == null) return "";
        return value.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
    }

    private String subject(PaymentOrder order) {
        return order.getProductCode() == PaymentProduct.MEMBERSHIP ? "映刻短剧月度会员" : "映刻短剧单集解锁";
    }
}
