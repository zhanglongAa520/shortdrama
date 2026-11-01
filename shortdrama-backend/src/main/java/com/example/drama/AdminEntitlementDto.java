package com.example.drama;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class AdminEntitlementDto {
    private AdminEntitlementDto() {}
    public record MembershipRequest(@NotNull @Future Instant expiresAt) {}
    public record AdminUserResponse(Long id, String username, String nickname, UserRole role,
                                    Instant membershipExpiresAt, boolean membershipActive,
                                    List<Long> unlockedEpisodeIds) {}
    public record EpisodeUnlockResponse(Long userId, Long episodeId, Instant grantedAt) {}
}
