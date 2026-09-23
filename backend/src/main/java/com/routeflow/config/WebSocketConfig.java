package com.routeflow.config;

import com.routeflow.security.StompAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;

    @Value("${routeflow.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker. For multi-instance scaling, swap for a Redis/RabbitMQ STOMP relay
        // (see PRD stretch goal: "Redis pub/sub for scaling WebSocket fan-out").
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Plain WebSocket endpoint (no SockJS fallback needed by modern browsers).
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","));

        // SockJS-wrapped endpoint for clients/proxies that need an HTTP fallback.
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOrigins(allowedOrigins.split(","))
                .withSockJS();
    }
}
