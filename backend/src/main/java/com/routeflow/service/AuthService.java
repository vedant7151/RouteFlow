package com.routeflow.service;

import com.routeflow.domain.User;
import com.routeflow.dto.auth.LoginRequest;
import com.routeflow.dto.auth.LoginResponse;
import com.routeflow.dto.auth.RegisterRequest;
import com.routeflow.exception.BadRequestException;
import com.routeflow.repository.UserRepository;
import com.routeflow.security.JwtService;
import com.routeflow.security.RouteFlowUserDetails;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));

        return issueTokens(user);
    }

    /** Exchanges a valid refresh token for a fresh access + refresh pair. */
    public LoginResponse refresh(String refreshToken) {
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new BadCredentialsException("Not a refresh token");
            }
            User user = userRepository.findByEmailIgnoreCase(jwtService.extractEmail(refreshToken))
                    .orElseThrow(() -> new BadCredentialsException("Unknown user"));
            if (!user.isEnabled()) {
                throw new BadCredentialsException("Account disabled");
            }
            return issueTokens(user);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
    }

    private LoginResponse issueTokens(User user) {
        RouteFlowUserDetails userDetails = new RouteFlowUserDetails(user);
        return new LoginResponse(
                jwtService.generateToken(userDetails), jwtService.generateRefreshToken(userDetails),
                "Bearer", jwtService.getAccessTokenTtlSeconds(),
                user.getId(), user.getName(), user.getEmail(), user.getRole().name()
        );
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Email already registered");
        }
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .enabled(true)
                .build();
        return userRepository.save(user);
    }
}
