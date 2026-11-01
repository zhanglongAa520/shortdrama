package com.example.drama;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class DramaDto {
    private DramaDto() {}

    public record EpisodeResponse(Long id, String title, String videoUrl, int durationSeconds,
                                  boolean free, boolean locked) {}

    public record DramaResponse(Long id, String title, String description, String genre,
                                String coverUrl, DramaStatus status, boolean featured,
                                int recommendationRank, long viewCount, List<EpisodeResponse> episodes) {}

    public record EpisodeRequest(Long id, @NotBlank String title, @NotBlank String videoUrl,
                                 @Min(1) int durationSeconds, boolean free) {}

    public record DramaRequest(@NotBlank String title, String description, @NotBlank String genre,
                               @NotBlank String coverUrl, @NotNull DramaStatus status,
                               @NotNull List<@Valid EpisodeRequest> episodes) {}

    public record Overview(long totalDramas, long publishedDramas, long totalEpisodes) {}
    public record RecommendationRequest(boolean featured, @Min(0) int recommendationRank) {}
    public record RecommendationsResponse(List<DramaResponse> featured, List<DramaResponse> hot) {}
}
