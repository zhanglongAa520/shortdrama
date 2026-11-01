package com.example.drama;

import com.example.drama.AdminEntitlementDto.AdminUserResponse;
import com.example.drama.AdminEntitlementDto.EpisodeUnlockResponse;
import com.example.drama.AdminEntitlementDto.MembershipRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminEntitlementController {
    private final AdminEntitlementService service;
    public AdminEntitlementController(AdminEntitlementService service) { this.service = service; }

    @GetMapping
    public List<AdminUserResponse> users() { return service.users(); }
    @PostMapping("/{userId}/membership")
    public AdminUserResponse membership(@PathVariable Long userId,
                                        @Valid @RequestBody MembershipRequest request) {
        return service.grantMembership(userId, request.expiresAt());
    }
    @DeleteMapping("/{userId}/membership") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeMembership(@PathVariable Long userId) { service.revokeMembership(userId); }
    @PostMapping("/{userId}/episode-unlocks/{episodeId}")
    public EpisodeUnlockResponse unlock(@PathVariable Long userId, @PathVariable Long episodeId) {
        return service.grantEpisode(userId, episodeId);
    }
    @DeleteMapping("/{userId}/episode-unlocks/{episodeId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeUnlock(@PathVariable Long userId, @PathVariable Long episodeId) {
        service.revokeEpisode(userId, episodeId);
    }
}
