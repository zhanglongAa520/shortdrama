package com.example.drama;

import com.example.drama.DramaDto.DramaResponse;
import com.example.drama.UserLibraryDto.EntitlementsResponse;
import com.example.drama.UserLibraryDto.HistoryResponse;
import com.example.drama.UserLibraryDto.PlaybackResponse;
import com.example.drama.UserLibraryDto.ProgressRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserLibraryController {
    private final UserLibraryService service;
    public UserLibraryController(UserLibraryService service) { this.service = service; }

    @GetMapping("/me/favorites")
    public List<DramaResponse> favorites(Authentication auth) { return service.favorites(auth.getName()); }
    @PostMapping("/me/favorites/{dramaId}")
    public DramaResponse favorite(@PathVariable Long dramaId, Authentication auth) {
        return service.favorite(auth.getName(), dramaId);
    }
    @DeleteMapping("/me/favorites/{dramaId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@PathVariable Long dramaId, Authentication auth) {
        service.unfavorite(auth.getName(), dramaId);
    }

    @GetMapping("/me/history")
    public List<HistoryResponse> history(Authentication auth) { return service.history(auth.getName()); }
    @PutMapping("/me/history/{episodeId}")
    public HistoryResponse progress(@PathVariable Long episodeId, @Valid @RequestBody ProgressRequest request,
                                    Authentication auth) {
        return service.saveProgress(auth.getName(), episodeId, request.progressSeconds());
    }
    @DeleteMapping("/me/history/{dramaId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearDramaHistory(@PathVariable Long dramaId, Authentication auth) {
        service.clearDramaHistory(auth.getName(), dramaId);
    }
    @DeleteMapping("/me/history") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(Authentication auth) { service.clearHistory(auth.getName()); }

    @GetMapping("/me/entitlements")
    public EntitlementsResponse entitlements(Authentication auth) { return service.entitlements(auth.getName()); }

    @PostMapping("/dramas/{dramaId}/episodes/{episodeId}/playback")
    public PlaybackResponse playback(@PathVariable Long dramaId, @PathVariable Long episodeId,
                                     Authentication auth) {
        return service.playback(auth.getName(), dramaId, episodeId);
    }
}
