package com.example.drama;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class UserLibraryDto {
    private UserLibraryDto() {}

    public record ProgressRequest(@NotNull Double progressSeconds) {}
    public record HistoryResponse(Long dramaId, String dramaTitle, String coverUrl,
                                  Long episodeId, String episodeTitle, double progressSeconds,
                                  int durationSeconds, boolean completed, Instant updatedAt) {}
    public record EntitlementsResponse(boolean membershipActive, Instant membershipExpiresAt,
                                       List<Long> unlockedEpisodeIds) {}
    public record PlaybackResponse(DramaDto.EpisodeResponse episode, double resumeAtSeconds,
                                   Long nextEpisodeId) {}
}
