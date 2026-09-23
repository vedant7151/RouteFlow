package com.routeflow.security;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * The WebSocket handshake itself is permitAll (browsers cannot set an Authorization header on it),
 * so identity is established on the STOMP CONNECT frame instead: clients send
 * "Authorization: Bearer &lt;access token&gt;" as a STOMP header. Unauthenticated CONNECTs are rejected,
 * and the resulting Principal is what @MessageMapping handlers use for ownership checks.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing Authorization header on STOMP CONNECT");
        }

        try {
            String token = header.substring(7);
            if (jwtService.isRefreshToken(token)) {
                throw new MessagingException("Refresh tokens cannot open a WebSocket");
            }
            UserDetails details = userDetailsService.loadUserByUsername(jwtService.extractEmail(token));
            if (!(details instanceof RouteFlowUserDetails user) || !jwtService.isTokenValid(token, user) || !user.isEnabled()) {
                throw new MessagingException("Invalid or expired token");
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        } catch (MessagingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MessagingException("Invalid or expired token");
        }
        return message;
    }
}
