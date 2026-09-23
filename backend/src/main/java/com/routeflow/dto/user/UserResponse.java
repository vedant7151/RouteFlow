package com.routeflow.dto.user;

import com.routeflow.domain.User;

public record UserResponse(
        String id,
        String name,
        String email,
        String role,
        boolean enabled
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isEnabled());
    }
}
