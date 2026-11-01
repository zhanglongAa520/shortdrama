package com.example.drama;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class PlaybackEventDto {
    private PlaybackEventDto() {}

    public record CreateRequest(@NotNull UUID eventId, @NotNull Long episodeId) {}
    public record Response(UUID eventId, Long episodeId, Long dramaId, Instant startedAt) {}
}
