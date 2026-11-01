package com.example.drama;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDto {
    private AuthDto() {}

    public record UserResponse(Long id, String username, String nickname, UserRole role) {}
    public record CsrfResponse(String token, String headerName) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 64) String nickname) {}
}
