package com.example.drama;

import java.util.List;
import java.util.Set;

public enum IntegrationProvider {
    WECHAT(
            List.of("appId", "mchId", "merchantSerialNo", "apiV3Key", "privateKey", "notifyUrl"),
            Set.of("apiV3Key", "privateKey")),
    ALIPAY(
            List.of("appId", "sellerId", "privateKey", "publicKey", "notifyUrl", "gateway"),
            Set.of("privateKey", "publicKey")),
    CSJ_ANDROID(
            List.of("appId", "placementId", "securityKey", "rewardName", "rewardAmount"),
            Set.of("securityKey")),
    CSJ_IOS(
            List.of("appId", "placementId", "securityKey", "rewardName", "rewardAmount"),
            Set.of("securityKey"));

    private final List<String> fields;
    private final Set<String> secretFields;

    IntegrationProvider(List<String> fields, Set<String> secretFields) {
        this.fields = fields;
        this.secretFields = secretFields;
    }

    public List<String> fields() { return fields; }
    public Set<String> secretFields() { return secretFields; }
}
