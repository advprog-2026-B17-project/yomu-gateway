package com.yomu.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/actuator",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/google"
    );

    private final Timer keyCreationTimer;
    private final Timer jwtParseTimer;
    private final Timer pathCheckTimer;
    private final Timer headerMutationTimer;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    public JwtAuthenticationFilter(MeterRegistry registry) {
        this.keyCreationTimer = Timer.builder("gateway.jwt.key-creation")
                .description("Time to create SecretKey from JWT secret")
                .register(registry);
        this.jwtParseTimer = Timer.builder("gateway.jwt.parse")
                .description("Time to parse and verify JWT signature")
                .register(registry);
        this.pathCheckTimer = Timer.builder("gateway.jwt.path-check")
                .description("Time to check if path is public")
                .register(registry);
        this.headerMutationTimer = Timer.builder("gateway.jwt.header-mutation")
                .description("Time to mutate request headers")
                .register(registry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();

        // Handle CORS preflight immediately
        if ("OPTIONS".equals(method)) {
            return chain.filter(exchange.mutate().request(withTrustedHeaders(request, null, null, null)).build());
        }

        // Path check timing
        boolean isPublic = pathCheckTimer.record(() -> isPublicPath(path));
        if (isPublic) {
            return chain.filter(exchange.mutate().request(withTrustedHeaders(request, null, null, null)).build());
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            // SecretKey creation timing
            SecretKey key = keyCreationTimer.record(() ->
                    Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)));

            // JWT parse timing
            Claims claims = jwtParseTimer.record(() ->
                    Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload());

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String username = claims.get("username", String.class);

            // Header mutation timing
            ServerHttpRequest mutatedRequest = headerMutationTimer.record(() ->
                    withTrustedHeaders(
                            request,
                            userId,
                            role != null ? role : "student",
                            username != null ? username : ""
                    ));

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(publicPath -> path.equals(publicPath) || path.startsWith(publicPath + "/"));
    }

    private ServerHttpRequest withTrustedHeaders(ServerHttpRequest request,
                                                 String userId,
                                                 String role,
                                                 String username) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                    headers.remove("X-Username");
                    headers.remove("X-Gateway-Secret");

                    if (StringUtils.hasText(userId)) {
                        headers.set("X-User-Id", userId);
                    }
                    if (StringUtils.hasText(role)) {
                        headers.set("X-User-Role", role);
                    }
                    if (username != null) {
                        headers.set("X-Username", username);
                    }
                    if (StringUtils.hasText(gatewaySharedSecret)) {
                        headers.set("X-Gateway-Secret", gatewaySharedSecret);
                    }
                })
                .build();
    }
}