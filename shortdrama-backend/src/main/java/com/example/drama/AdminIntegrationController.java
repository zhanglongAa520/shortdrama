package com.example.drama;

import com.example.drama.AdminIntegrationDto.IntegrationResponse;
import com.example.drama.AdminIntegrationDto.UpdateIntegrationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/integrations")
public class AdminIntegrationController {
    private final IntegrationConfigService service;

    public AdminIntegrationController(IntegrationConfigService service) { this.service = service; }

    @GetMapping("/{provider}")
    public IntegrationResponse get(@PathVariable IntegrationProvider provider) {
        return service.adminResponse(provider);
    }

    @PutMapping("/{provider}")
    public IntegrationResponse update(@PathVariable IntegrationProvider provider,
                                      @Valid @RequestBody UpdateIntegrationRequest request) {
        return service.update(provider, request);
    }
}
