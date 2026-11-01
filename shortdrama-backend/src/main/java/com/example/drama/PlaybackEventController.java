package com.example.drama;

import com.example.drama.PlaybackEventDto.CreateRequest;
import com.example.drama.PlaybackEventDto.Response;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/playback-events")
public class PlaybackEventController {
    private final PlaybackEventService service;

    public PlaybackEventController(PlaybackEventService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest request, Authentication authentication) {
        return service.record(authentication.getName(), request);
    }
}
