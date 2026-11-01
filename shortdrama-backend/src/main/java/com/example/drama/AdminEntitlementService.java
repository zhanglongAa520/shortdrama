package com.example.drama;

import com.example.drama.AdminEntitlementDto.AdminUserResponse;
import com.example.drama.AdminEntitlementDto.EpisodeUnlockResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminEntitlementService {
    private final AppUserRepository users;
    private final EpisodeRepository episodes;
    private final EpisodeUnlockRepository unlocks;
    private final EpisodeEntitlementService entitlementService;

    public AdminEntitlementService(AppUserRepository users, EpisodeRepository episodes,
                                   EpisodeUnlockRepository unlocks, EpisodeEntitlementService entitlementService) {
        this.users = users;
        this.episodes = episodes;
        this.unlocks = unlocks;
        this.entitlementService = entitlementService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users() {
        return users.findAllByOrderByIdAsc().stream().map(this::response).toList();
    }

    @Transactional
    public AdminUserResponse grantMembership(Long userId, Instant expiresAt) {
        AppUser user = lockedUser(userId);
        user.setMembershipExpiresAt(expiresAt);
        return response(user);
    }

    @Transactional
    public void revokeMembership(Long userId) {
        AppUser user = lockedUser(userId);
        user.setMembershipExpiresAt(null);
    }

    @Transactional
    public EpisodeUnlockResponse grantEpisode(Long userId, Long episodeId) {
        AppUser user = findUser(userId);
        Episode episode = findEpisode(episodeId);
        EpisodeUnlock unlock = entitlementService.grantEpisodeUnlock(user, episode);
        return new EpisodeUnlockResponse(userId, episodeId, unlock.getGrantedAt());
    }

    @Transactional
    public void revokeEpisode(Long userId, Long episodeId) {
        findUser(userId);
        unlocks.deleteByUserIdAndEpisodeId(userId, episodeId);
    }

    private AppUser findUser(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private AppUser lockedUser(Long id) {
        return users.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private Episode findEpisode(Long id) {
        return episodes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分集不存在"));
    }

    private AdminUserResponse response(AppUser user) {
        boolean active = user.getMembershipExpiresAt() != null && user.getMembershipExpiresAt().isAfter(Instant.now());
        List<Long> unlockedEpisodeIds = unlocks.findByUserIdOrderByGrantedAtDesc(user.getId()).stream()
                .map(unlock -> unlock.getEpisode().getId()).toList();
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                user.getMembershipExpiresAt(), active, unlockedEpisodeIds);
    }
}
