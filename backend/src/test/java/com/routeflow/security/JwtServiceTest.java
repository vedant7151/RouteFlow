package com.routeflow.security;

import com.routeflow.domain.User;
import com.routeflow.domain.enums.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long!!";

    private final RouteFlowUserDetails user = new RouteFlowUserDetails(User.builder()
            .id("u1").email("driver1@routeflow.dev").passwordHash("x").role(Role.DRIVER).enabled(true).build());

    @Test
    void accessAndRefreshTokensAreDistinguishable() {
        JwtService jwt = new JwtService(SECRET, 60, 1440);

        assertFalse(jwt.isRefreshToken(jwt.generateToken(user)));
        assertTrue(jwt.isRefreshToken(jwt.generateRefreshToken(user)));
    }

    @Test
    void tokenCarriesIdentityAndValidatesForItsUser() {
        JwtService jwt = new JwtService(SECRET, 60, 1440);
        String token = jwt.generateToken(user);

        assertEquals("driver1@routeflow.dev", jwt.extractEmail(token));
        assertEquals("u1", jwt.extractUserId(token));
        assertEquals("DRIVER", jwt.extractRole(token));
        assertTrue(jwt.isTokenValid(token, user));
    }

    @Test
    void expiredTokensAreRejected() {
        JwtService expired = new JwtService(SECRET, -1, -1);

        assertThrows(JwtException.class, () -> expired.isTokenValid(expired.generateToken(user), user));
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String forged = new JwtService("a-completely-different-secret-key-of-32+bytes!!", 60, 1440).generateToken(user);

        assertThrows(JwtException.class, () -> new JwtService(SECRET, 60, 1440).extractEmail(forged));
    }
}
