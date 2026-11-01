package com.example.drama;

import com.example.drama.PlaybackEventDto.CreateRequest;
import com.example.drama.PlaybackEventDto.Response;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaybackEventService {
    private final AppUserRepository users;
    private final PlaybackEventRepository events;
    private final EpisodeEntitlementService entitlements;
    private final PlaybackEventWriter writer;

    public PlaybackEventService(AppUserRepository users, PlaybackEventRepository events,
                                EpisodeEntitlementService entitlements, PlaybackEventWriter writer) {
        this.users = users;
        this.events = events;
        this.entitlements = entitlements;
        this.writer = writer;
    }

    public Response record(String username, CreateRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getRole() != UserRole.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员不能记录运营播放事件");
        }
        String id = request.eventId().toString();
        PlaybackEvent existing = events.findById(id).orElse(null);
        if (existing != null) return idempotent(existing, user.getId(), request.episodeId());

        Episode episode = entitlements.requireWatchable(username, request.episodeId());
        Long dramaId = episode.getDrama().getId();
        try {
            return response(writer.create(id, user.getId(), dramaId, episode.getId()));
        } catch (DataIntegrityViolationException exception) {
            PlaybackEvent concurrent = events.findById(id).orElseThrow(() -> exception);
            return idempotent(concurrent, user.getId(), request.episodeId());
        }
    }

    private Response idempotent(PlaybackEvent event, Long userId, Long episodeId) {
        if (!event.getUser().getId().equals(userId) || !event.getEpisode().getId().equals(episodeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "eventId 已被其他播放事件使用");
        }
        return response(event);
    }

    private Response response(PlaybackEvent event) {
        return new Response(UUID.fromString(event.getId()), event.getEpisode().getId(),
                event.getDrama().getId(), event.getStartedAt());
    }
}
