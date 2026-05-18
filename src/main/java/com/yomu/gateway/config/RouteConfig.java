package com.yomu.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Value("${CORE_API_URL:http://localhost:8080}")
    private String coreApiUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .uri(coreApiUrl))
                .route("core-api", r -> r
                        .path("/api/**")
                        .uri(coreApiUrl))
                .route("admin-api", r -> r
                        .path("/admin/**")
                        .uri(coreApiUrl))
                .build();
    }
}
