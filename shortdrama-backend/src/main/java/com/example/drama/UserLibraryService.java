package com.example.drama;

import com.example.drama.DramaDto.DramaResponse;
import com.example.drama.UserLibraryDto.EntitlementsResponse;
import com.example.drama.UserLibraryDto.HistoryResponse;
import com.example.drama.UserLibraryDto.PlaybackResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserLibraryService {
    private final AppUserRepository users;
    private final DramaRepository dramas;
    private final FavoriteRepository favorites;
    private final WatchHistoryRepository histories;
    private final EpisodeUnlockRepository unlocks;
    private final EpisodeEntitlementService entitlements;
    private final DramaViewRepository views;
    private final DramaService dramaService;
    private final SocialCountCache socialCache;

    public UserLibraryService(AppUserRepository users, DramaRepository dramas, FavoriteRepository favorites,
                              WatchHistoryRepository histories, EpisodeUnlockRepository unlocks,
                              EpisodeEntitlementService entitlements, DramaViewRepository views,
                              DramaService dramaService, SocialCountCache socialCache) {
        this.users = users;
        this.dramas = dramas;
        this.favorites = favorites;
        this.histories = histories;
        this.unlocks = unlocks;
        this.entitlements = entitlements;
        this.views = views;
        this.dramaService = dramaService;
        this.socialCache = socialCache;
    }

    @Transactional(readOnly = true)
    public List<DramaResponse> favorites(String username) {
        AppUser user = user(username);
        return favorites.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(favorite -> favorite.getDrama().getStatus() == DramaStatus.PUBLISHED)
                .map(favorite -> dramaService.toPublicResponse(favorite.getDrama(), username)).toList();
    }

    @Transactional
    public DramaResponse favorite(String username, Long dramaId) {
        AppUser user = lockedUser(username);
        Drama drama = publishedDrama(dramaId);
        if (favorites.findByUserIdAndDramaId(user.getId(), dramaId).isEmpty()) {
            Favorite favorite = new Favorite();
            favorite.setUser(user);
            favorite.setDrama(drama);
            favorites.save(favorite);
            socialCache.invalidateAfterCommit(dramaId);
        }
        return dramaService.toPublicResponse(drama, username);
    }

    @Transactional
    public void unfavorite(String username, Long dramaId) {
        AppUser user = lockedUser(username);
        if (favorites.deleteByUserIdAndDramaId(user.getId(), dramaId) > 0) {
            socialCache.invalidateAfterCommit(dramaId);
        }
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(String username) {
        AppUser user = user(username);
        return histories.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(item -> item.getDrama().getStatus() == DramaStatus.PUBLISHED)
                .map(this::historyResponse).toList();
    }

    @Transactional
    public HistoryResponse saveProgress(String username, Long episodeId, double progressSeconds) {
        if (!Double.isFinite(progressSeconds) || progressSeconds < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "播放进度必须是非负有限数");
        }
        Episode episode = entitlements.requireWatchable(username, episodeId);
        if (progressSeconds > episode.getDurationSeconds()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "播放进度不能超过分集时长");
        }
        AppUser user = lockedUser(username);
        WatchHistory item = histories.findByUserIdAndEpisodeId(user.getId(), episodeId).orElseGet(() -> {
            WatchHistory created = new WatchHistory();
            created.setUser(user);
            created.setDrama(episode.getDrama());
            created.setEpisode(episode);
            return created;
        });
        item.setProgressSeconds(progressSeconds);
        return historyResponse(histories.saveAndFlush(item));
    }

    @Transactional
    public void clearDramaHistory(String username, Long dramaId) {
        histories.deleteByUserIdAndDramaId(user(username).getId(), dramaId);
    }

    @Transactional
    public void clearHistory(String username) { histories.deleteByUserId(user(username).getId()); }

    @Transactional(readOnly = true)
    public EntitlementsResponse entitlements(String username) {
        AppUser user = user(username);
        boolean active = user.getMembershipExpiresAt() != null
                && user.getMembershipExpiresAt().isAfter(java.time.Instant.now());
        List<Long> episodeIds = unlocks.findByUserIdOrderByGrantedAtDesc(user.getId()).stream()
                .map(unlock -> unlock.getEpisode().getId()).toList();
        return new EntitlementsResponse(active, user.getMembershipExpiresAt(), episodeIds);
    }

    @Transactional
    public PlaybackResponse playback(String username, Long dramaId, Long episodeId) {
        Episode episode = entitlements.requireWatchable(username, episodeId);
        if (!episode.getDrama().getId().equals(dramaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分集不属于当前短剧");
        }
        AppUser user = user(username);
        Drama drama = dramas.findForUpdate(dramaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在"));
        if (!views.existsByUserIdAndDramaId(user.getId(), dramaId)) {
            DramaView view = new DramaView();
            view.setUser(user);
            view.setDrama(drama);
            views.save(view);
            drama.setViewCount(drama.getViewCount() + 1);
        }
        double resumeAt = histories.findByUserIdAndEpisodeId(user.getId(), episodeId)
                .map(WatchHistory::getProgressSeconds).orElse(0.0);
        List<Episode> ordered = drama.getEpisodes();
        int index = ordered.indexOf(episode);
        Long nextId = index >= 0 && index + 1 < ordered.size() ? ordered.get(index + 1).getId() : null;
        return new PlaybackResponse(dramaService.publicEpisodeResponse(episode, username), resumeAt, nextId);
    }

    private AppUser user(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private AppUser lockedUser(String username) {
        AppUser found = user(username);
        return users.findForUpdate(found.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Drama publishedDrama(Long id) {
        return dramas.findByIdAndStatus(id, DramaStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在"));
    }

    private HistoryResponse historyResponse(WatchHistory item) {
        Episode episode = item.getEpisode();
        Drama drama = item.getDrama();
        boolean completed = item.getProgressSeconds() / episode.getDurationSeconds() >= 0.95;
        return new HistoryResponse(drama.getId(), drama.getTitle(), drama.getCoverUrl(), episode.getId(),
                episode.getTitle(), item.getProgressSeconds(), episode.getDurationSeconds(), completed,
                item.getUpdatedAt());
    }
}
