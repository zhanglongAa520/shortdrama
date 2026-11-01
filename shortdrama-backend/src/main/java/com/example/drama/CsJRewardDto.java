package com.example.drama;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public final class CsJRewardDto {
    private CsJRewardDto() {}

    public record PlatformConfig(boolean enabled, boolean configured, String appId, String placementId) {}
    public record ConfigResponse(boolean enabled, Map<RewardPlatform, PlatformConfig> platforms) {}
    public record CreateAttemptRequest(@NotNull Long episodeId, @NotNull RewardPlatform platform) {}
    public record AttemptResponse(String id, RewardAttemptStatus status, RewardPlatform platform, Long episodeId,
                                  String providerUserId, String extra, Instant expiresAt) {}
    public record AttemptStatusResponse(String id, RewardAttemptStatus status, RewardPlatform platform,
                                        Long episodeId, Instant expiresAt, Instant grantedAt) {}
    public record CallbackResponse(boolean isValid) {}
}
