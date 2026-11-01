package com.example.drama;

import com.example.drama.AdminIntegrationDto.IntegrationResponse;
import com.example.drama.AdminIntegrationDto.UpdateIntegrationRequest;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IntegrationConfigService {
    public static final String ALIPAY_PRODUCTION = "PRODUCTION";
    public static final String ALIPAY_SANDBOX = "SANDBOX";
    public static final String ALIPAY_PRODUCTION_URL = "https://openapi.alipay.com/gateway.do";
    public static final String ALIPAY_SANDBOX_URL = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    public record EffectiveConfig(IntegrationProvider provider, boolean enabled, long version,
                                  Map<String, String> values) {
        public String value(String key) { return values.getOrDefault(key, ""); }
    }

    private final IntegrationConfigRepository repository;
    private final IntegrationCryptoService crypto;
    private final Environment environment;

    public IntegrationConfigService(IntegrationConfigRepository repository, IntegrationCryptoService crypto,
                                    Environment environment) {
        this.repository = repository;
        this.crypto = crypto;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public EffectiveConfig effective(IntegrationProvider provider) {
        Map<String, String> values = fallbackValues(provider);
        IntegrationConfig stored = repository.findById(provider).orElse(null);
        if (stored == null) return new EffectiveConfig(provider, fallbackEnabled(provider), 0, Map.copyOf(values));
        values.putAll(crypto.decrypt(provider, stored.getEncryptedValues()));
        return new EffectiveConfig(provider, stored.isEnabled(), stored.getConfigVersion(), Map.copyOf(values));
    }

    @Transactional(readOnly = true)
    public IntegrationResponse adminResponse(IntegrationProvider provider) {
        return response(effective(provider));
    }

    @Transactional
    public IntegrationResponse update(IntegrationProvider provider, UpdateIntegrationRequest request) {
        Map<String, String> submitted = request.values() == null ? Map.of() : request.values();
        List<String> clears = request.clearSecrets() == null ? List.of() : request.clearSecrets();
        validateKeys(provider, submitted.keySet(), clears);

        IntegrationConfig config = repository.findForUpdate(provider).orElse(null);
        Map<String, String> overrides = config == null
                ? new LinkedHashMap<>() : crypto.decrypt(provider, config.getEncryptedValues());
        for (Map.Entry<String, String> entry : submitted.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (provider.secretFields().contains(entry.getKey()) && value.isBlank()) continue;
            overrides.put(entry.getKey(), validateValue(provider, entry.getKey(), value));
        }
        for (String key : clears) {
            if (StringUtils.hasText(submitted.get(key))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密钥不能同时设置和清除: " + key);
            }
            overrides.put(key, "");
        }

        Map<String, String> candidateValues = fallbackValues(provider);
        candidateValues.putAll(overrides);
        EffectiveConfig candidate = new EffectiveConfig(provider, request.enabled(),
                config == null ? 1 : config.getConfigVersion() + 1, Map.copyOf(candidateValues));
        if (request.enabled() && !isConfigured(candidate)) {
            badRequest("启用渠道前必须填写全部必需配置");
        }

        if (config == null) {
            config = new IntegrationConfig();
            config.setProvider(provider);
            config.setConfigVersion(1);
        } else {
            config.setConfigVersion(config.getConfigVersion() + 1);
        }
        config.setEnabled(request.enabled());
        config.setEncryptedValues(crypto.encrypt(provider, overrides));
        config.setUpdatedAt(Instant.now());
        repository.saveAndFlush(config);
        return response(effective(provider));
    }

    public boolean isConfigured(EffectiveConfig config) {
        for (String field : config.provider().fields()) {
            if (!StringUtils.hasText(config.value(field))) return false;
        }
        if (config.provider() == IntegrationProvider.WECHAT) {
            return config.value("apiV3Key").getBytes(java.nio.charset.StandardCharsets.UTF_8).length == 32;
        }
        if (config.provider() == IntegrationProvider.ALIPAY) {
            return Set.of(ALIPAY_PRODUCTION, ALIPAY_SANDBOX).contains(config.value("gateway"));
        }
        try {
            return Integer.parseInt(config.value("rewardAmount")) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private IntegrationResponse response(EffectiveConfig effective) {
        Map<String, String> visible = new LinkedHashMap<>();
        List<String> secretsSet = effective.provider().fields().stream()
                .filter(field -> effective.provider().secretFields().contains(field))
                .filter(field -> StringUtils.hasText(effective.value(field))).toList();
        for (String field : effective.provider().fields()) {
            if (!effective.provider().secretFields().contains(field)) visible.put(field, effective.value(field));
        }
        return new IntegrationResponse(effective.provider(), effective.enabled(), isConfigured(effective),
                Map.copyOf(visible), secretsSet);
    }

    private void validateKeys(IntegrationProvider provider, Set<String> submitted, List<String> clears) {
        for (String key : submitted) {
            if (!provider.fields().contains(key)) badRequest("不支持的配置字段: " + key);
        }
        for (String key : clears) {
            if (!provider.secretFields().contains(key)) badRequest("只能清除密钥字段: " + key);
        }
    }

    private String validateValue(IntegrationProvider provider, String key, String value) {
        int maximum = provider.secretFields().contains(key) ? 20_000 : 1_000;
        if (value.length() > maximum) badRequest("配置字段过长: " + key);
        if ("gateway".equals(key)) {
            String normalized = value.toUpperCase(java.util.Locale.ROOT);
            if (!Set.of(ALIPAY_PRODUCTION, ALIPAY_SANDBOX).contains(normalized)) {
                badRequest("gateway 只能是 PRODUCTION 或 SANDBOX");
            }
            return normalized;
        }
        if ("rewardAmount".equals(key) && StringUtils.hasText(value)) {
            try {
                int amount = Integer.parseInt(value);
                if (amount <= 0 || amount > 1_000_000) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                badRequest("rewardAmount 必须是 1 到 1000000 的整数");
            }
        }
        if ("notifyUrl".equals(key) && StringUtils.hasText(value)) validateNotifyUrl(value);
        if (provider == IntegrationProvider.WECHAT && "apiV3Key".equals(key)
                && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length != 32) {
            badRequest("apiV3Key 必须是 32 字节");
        }
        return value;
    }

    private void validateNotifyUrl(String value) {
        try {
            URI uri = URI.create(value);
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (!("https".equalsIgnoreCase(uri.getScheme()) || localHttp) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                badRequest("notifyUrl 必须是 HTTPS 绝对地址（本地开发可使用 localhost HTTP）");
            }
        } catch (IllegalArgumentException exception) {
            badRequest("notifyUrl 格式无效");
        }
    }

    private boolean fallbackEnabled(IntegrationProvider provider) {
        return switch (provider) {
            case WECHAT -> bool("payment.wechat.enabled");
            case ALIPAY -> bool("payment.alipay.enabled");
            case CSJ_ANDROID -> bool("app.rewards.csj.enabled") && bool("app.rewards.csj.android.enabled");
            case CSJ_IOS -> bool("app.rewards.csj.enabled") && bool("app.rewards.csj.ios.enabled");
        };
    }

    private Map<String, String> fallbackValues(IntegrationProvider provider) {
        Map<String, String> values = new LinkedHashMap<>();
        switch (provider) {
            case WECHAT -> {
                values.put("appId", property("payment.wechat.app-id"));
                values.put("mchId", property("payment.wechat.mch-id"));
                values.put("merchantSerialNo", property("payment.wechat.merchant-serial-no"));
                values.put("apiV3Key", property("payment.wechat.api-v3-key"));
                values.put("privateKey", wechatPrivateKey());
                values.put("notifyUrl", property("payment.wechat.notify-url"));
            }
            case ALIPAY -> {
                values.put("appId", property("payment.alipay.app-id"));
                values.put("sellerId", property("payment.alipay.seller-id"));
                values.put("privateKey", property("payment.alipay.private-key"));
                values.put("publicKey", property("payment.alipay.alipay-public-key"));
                values.put("notifyUrl", property("payment.alipay.notify-url"));
                values.put("gateway", normalizeGateway(property("payment.alipay.gateway")));
            }
            case CSJ_ANDROID, CSJ_IOS -> {
                String prefix = provider == IntegrationProvider.CSJ_ANDROID
                        ? "app.rewards.csj.android." : "app.rewards.csj.ios.";
                values.put("appId", property(prefix + "app-id"));
                values.put("placementId", property(prefix + "placement-id"));
                values.put("securityKey", property(prefix + "security-key"));
                values.put("rewardName", property(prefix + "reward-name"));
                values.put("rewardAmount", property(prefix + "reward-amount"));
            }
        }
        return values;
    }

    private String wechatPrivateKey() {
        String direct = property("payment.wechat.private-key");
        if (StringUtils.hasText(direct)) return direct;
        String path = property("payment.wechat.private-key-path");
        if (!StringUtils.hasText(path)) return "";
        try {
            Path keyPath = Path.of(path).toAbsolutePath().normalize();
            return Files.isRegularFile(keyPath) ? Files.readString(keyPath) : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private String normalizeGateway(String value) {
        if (!StringUtils.hasText(value) || ALIPAY_PRODUCTION.equalsIgnoreCase(value)
                || ALIPAY_PRODUCTION_URL.equals(value)) return ALIPAY_PRODUCTION;
        if (ALIPAY_SANDBOX.equalsIgnoreCase(value) || ALIPAY_SANDBOX_URL.equals(value)) return ALIPAY_SANDBOX;
        return "";
    }

    private boolean bool(String key) { return environment.getProperty(key, Boolean.class, false); }
    private String property(String key) { return environment.getProperty(key, ""); }
    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
