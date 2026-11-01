package com.example.drama;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.rewards.csj")
public class CsJProperties {
    private boolean enabled;
    private Duration attemptTtl = Duration.ofMinutes(10);
    private Platform android = new Platform();
    private Platform ios = new Platform();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getAttemptTtl() { return attemptTtl; }
    public void setAttemptTtl(Duration attemptTtl) { this.attemptTtl = attemptTtl; }
    public Platform getAndroid() { return android; }
    public void setAndroid(Platform android) { this.android = android; }
    public Platform getIos() { return ios; }
    public void setIos(Platform ios) { this.ios = ios; }

    public Platform platform(RewardPlatform platform) {
        return platform == RewardPlatform.ANDROID ? android : ios;
    }

    public static class Platform {
        private boolean enabled;
        private String appId;
        private String placementId;
        private String securityKey;
        private String rewardName;
        private int rewardAmount;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getPlacementId() { return placementId; }
        public void setPlacementId(String placementId) { this.placementId = placementId; }
        public String getSecurityKey() { return securityKey; }
        public void setSecurityKey(String securityKey) { this.securityKey = securityKey; }
        public String getRewardName() { return rewardName; }
        public void setRewardName(String rewardName) { this.rewardName = rewardName; }
        public int getRewardAmount() { return rewardAmount; }
        public void setRewardAmount(int rewardAmount) { this.rewardAmount = rewardAmount; }

        public boolean isConfigured() {
            return StringUtils.hasText(appId) && StringUtils.hasText(placementId)
                    && StringUtils.hasText(securityKey) && StringUtils.hasText(rewardName)
                    && rewardAmount > 0;
        }
    }
}
