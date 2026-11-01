package com.example.drama;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class InteractionDto {
    private InteractionDto() {}

    public record CreateRequest(@NotNull InteractionType type, @NotBlank String text,
                                Long episodeId, Double at, Integer lane) {}
    public record StatusRequest(@NotNull InteractionStatus status) {}
    public record Response(Long id, InteractionType type, String text, Long dramaId,
                           Long episodeId, Double at, Integer lane, InteractionStatus status,
                           String username, String nickname, Instant createdAt) {}
}
