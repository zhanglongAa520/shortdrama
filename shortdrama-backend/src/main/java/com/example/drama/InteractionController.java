package com.example.drama;

import com.example.drama.InteractionDto.CreateRequest;
import com.example.drama.InteractionDto.Response;
import com.example.drama.InteractionDto.StatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InteractionController {
    private final InteractionService service;

    public InteractionController(InteractionService service) { this.service = service; }

    @GetMapping("/dramas/{dramaId}/interactions")
    public List<Response> visible(@PathVariable Long dramaId, @RequestParam InteractionType type,
                                  @RequestParam(required = false) Long episodeId,
                                  Authentication authentication) {
        return service.visible(dramaId, type, episodeId, authentication.getName());
    }

    @PostMapping("/dramas/{dramaId}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable Long dramaId, @Valid @RequestBody CreateRequest request,
                           Authentication authentication) {
        return service.create(dramaId, request, authentication.getName());
    }

    @GetMapping("/admin/interactions")
    public List<Response> adminList(@RequestParam(required = false) InteractionType type,
                                    @RequestParam(required = false) InteractionStatus status) {
        return service.adminList(type, status);
    }

    @PatchMapping("/admin/interactions/{id}")
    public Response setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.setStatus(id, request.status());
    }

    @DeleteMapping("/admin/interactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
