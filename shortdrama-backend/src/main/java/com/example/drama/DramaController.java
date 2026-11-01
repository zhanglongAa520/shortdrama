package com.example.drama;

import com.example.drama.DramaDto.DramaRequest;
import com.example.drama.DramaDto.DramaResponse;
import com.example.drama.DramaDto.Overview;
import com.example.drama.DramaDto.RecommendationRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
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
public class DramaController {
    private final DramaService service;

    public DramaController(DramaService service) {
        this.service = service;
    }

    @GetMapping("/dramas")
    public List<DramaResponse> published(Authentication authentication) {
        return service.published(authentication.getName());
    }

    @GetMapping("/dramas/recommendations")
    public DramaDto.RecommendationsResponse recommendations(Authentication authentication) {
        return service.recommendations(authentication.getName());
    }

    @GetMapping("/dramas/{id}")
    public DramaResponse published(@PathVariable Long id, Authentication authentication) {
        return service.published(id, authentication.getName());
    }

    @GetMapping("/admin/dramas")
    public List<DramaResponse> all() { return service.all(); }

    @PostMapping("/admin/dramas")
    @ResponseStatus(HttpStatus.CREATED)
    public DramaResponse create(@Valid @RequestBody DramaRequest request) { return service.create(request); }

    @PutMapping("/admin/dramas/{id}")
    public DramaResponse update(@PathVariable Long id, @Valid @RequestBody DramaRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/admin/dramas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @GetMapping("/admin/overview")
    public Overview overview() { return service.overview(); }

    @PatchMapping("/admin/dramas/{id}/recommendation")
    public DramaResponse recommend(@PathVariable Long id, @Valid @RequestBody RecommendationRequest request) {
        return service.recommend(id, request.featured(), request.recommendationRank());
    }
}
