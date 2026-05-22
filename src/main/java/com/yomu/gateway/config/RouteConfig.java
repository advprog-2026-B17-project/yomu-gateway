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

    @Value("${GAMIFICATION_API_URL:http://localhost:8081}")
    private String gamificationApiUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("gamification-api", r -> r
                        .path("/api/gamification/**", "/api/notifications/**", "/api/achievements/**",
                              "/api/missions/**", "/api/clans/**",
                              "/api/admin/achievements/**", "/api/admin/missions/**", "/api/admin/seasons/**")
                        .uri(gamificationApiUrl))
                .route("core-api", r -> r
                        .path("/api/**")
                        .uri(coreApiUrl))
                .build();
    }
}
