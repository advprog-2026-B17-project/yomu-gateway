package com.yomu.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RouteConfig {

    @Value("${CORE_API_URL:http://localhost:8080}")
    private String coreApiUrl;

    @Value("${GAMIFICATION_ENGINE_URL:http://localhost:8081}")
    private String gamificationEngineUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("notifications-service", r -> r
                        .path("/api/notifications/**")
                        .uri(gamificationEngineUrl))
                .route("achievements-read-service", r -> r
                        .method(HttpMethod.GET)
                        .and()
                        .path("/api/achievements/**")
                        .uri(gamificationEngineUrl))
                .route("missions-read-service", r -> r
                        .method(HttpMethod.GET)
                        .and()
                        .path("/api/missions/*")
                        .uri(gamificationEngineUrl))
                .route("clans-read-service", r -> r
                        .method(HttpMethod.GET)
                        .and()
                        .path("/api/clans", "/api/clans/me", "/api/clans/leaderboard", "/api/clans/*/leaderboard")
                        .uri(gamificationEngineUrl))
                .route("core-api", r -> r
                        .path("/api/**")
                        .uri(coreApiUrl))
                .route("admin-api", r -> r
                        .path("/admin/**")
                        .uri(coreApiUrl))
                .build();
    }
}
