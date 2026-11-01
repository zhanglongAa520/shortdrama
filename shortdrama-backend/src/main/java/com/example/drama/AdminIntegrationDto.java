package com.example.drama;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public final class AdminIntegrationDto {
    private AdminIntegrationDto() {}

    public record IntegrationResponse(IntegrationProvider provider, boolean enabled, boolean configured,
                                      Map<String, String> values, List<String> secretFieldsSet) {}

    public record UpdateIntegrationRequest(@NotNull Boolean enabled, Map<String, String> values,
                                           List<String> clearSecrets) {}
}
