package com.example.drama;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.social-cache")
public class SocialCacheProperties {
    private boolean enabled;
    private String keyPrefix = "ai-short-drama:social:";
    private Duration ttl = Duration.ofSeconds(30);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyPrefix() {
        return StringUtils.hasText(keyPrefix) ? keyPrefix : "ai-short-drama:social:";
    }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public Duration getTtl() {
        return ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(30) : ttl;
    }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
}
