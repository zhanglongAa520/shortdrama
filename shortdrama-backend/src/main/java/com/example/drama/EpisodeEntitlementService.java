package com.example.drama;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EpisodeEntitlementService {
    private final AppUserRepository users;
    private final EpisodeRepository episodes;
    private final EpisodeUnlockRepository unlocks;

    public EpisodeEntitlementService(AppUserRepository users, EpisodeRepository episodes,
                                     EpisodeUnlockRepository unlocks) {
        this.users = users;
        this.episodes = episodes;
        this.unlocks = unlocks;
    }

    @Transactional(readOnly = true)
    public boolean canWatch(String username, Episode episode) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return canWatch(user, episode);
    }

    public boolean canWatch(AppUser user, Episode episode) {
        return user.getRole() == UserRole.ADMIN || episode.isFree()
                || (user.getMembershipExpiresAt() != null && user.getMembershipExpiresAt().isAfter(Instant.now()))
                || unlocks.existsByUserIdAndEpisodeId(user.getId(), episode.getId());
    }

    @Transactional(readOnly = true)
    public Episode requireWatchable(String username, Long episodeId) {
        Episode episode = episodes.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在"));
        if (episode.getDrama().getStatus() != DramaStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在");
        }
        if (!canWatch(username, episode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前分集需要会员或单集解锁");
        }
        return episode;
    }

    @Transactional(readOnly = true)
    public boolean canAccessVideoReference(Authentication authentication, String videoReference) {
        if (authentication == null || !authentication.isAuthenticated() || videoReference == null) return false;
        return episodes.findByVideoUrl(videoReference).stream()
                .filter(e -> e.getDrama().getStatus() == DramaStatus.PUBLISHED)
                .anyMatch(e -> canWatch(authentication.getName(), e));
    }

    public boolean isManagedVideoReference(String videoReference) {
        return videoReference != null && (videoReference.matches("/api/media/demo/[A-Za-z0-9._-]+\\.mp4")
                || videoReference.matches("asset:[1-9][0-9]*"));
    }

    @Transactional
    public synchronized EpisodeUnlock grantEpisodeUnlock(AppUser user, Episode episode) {
        AppUser lockedUser = users.findForUpdate(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        return unlocks.findByUserIdAndEpisodeId(user.getId(), episode.getId()).orElseGet(() -> {
            EpisodeUnlock unlock = new EpisodeUnlock();
            unlock.setUser(lockedUser);
            unlock.setEpisode(episode);
            return unlocks.saveAndFlush(unlock);
        });
    }
}
