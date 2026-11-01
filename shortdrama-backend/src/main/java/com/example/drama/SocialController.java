package com.example.drama;

import com.example.drama.SocialDto.Response;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SocialController {
    private final SocialService service;

    public SocialController(SocialService service) { this.service = service; }

    @GetMapping("/dramas/{dramaId}/social")
    public Response social(@PathVariable Long dramaId, Authentication auth) {
        return service.social(auth.getName(), dramaId);
    }

    @PutMapping("/me/likes/{dramaId}")
    public Response like(@PathVariable Long dramaId, Authentication auth) {
        return service.like(auth.getName(), dramaId);
    }

    @DeleteMapping("/me/likes/{dramaId}")
    public Response unlike(@PathVariable Long dramaId, Authentication auth) {
        return service.unlike(auth.getName(), dramaId);
    }
}
