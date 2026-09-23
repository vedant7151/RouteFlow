package com.routeflow.dto.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        String userId,
        String name,
        String email,
        String role
) {
}
