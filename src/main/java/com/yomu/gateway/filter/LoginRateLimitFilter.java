package com.yomu.gateway.filter;

import com.yomu.gateway.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class LoginRateLimitFilter implements GlobalFilter, Ordered {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final List<String> IP_HEADERS = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    );

    private final RateLimitConfig rateLimitConfig;

    public LoginRateLimitFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Only apply rate limit to login endpoint
        if (!LOGIN_PATH.equals(path)) {
            return chain.filter(exchange);
        }

        String clientIp = extractClientIp(request);
        Bucket bucket = rateLimitConfig.resolveBucket(clientIp);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().add(rateLimitConfig.getHeaderRateLimitRemaining(),
                    String.valueOf(probe.getRemainingTokens()));
            response.getHeaders().add(rateLimitConfig.getHeaderRateLimitReset(),
                    String.valueOf(Instant.now().plusSeconds(probe.getNanosToWaitForRefill() / 1_000_000_000).toEpochMilli()));

            return chain.filter(exchange);
        }

        // Rate limit exceeded
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(rateLimitConfig.getHeaderRetryAfter(),
                String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));

        return response.setComplete();
    }

    private String extractClientIp(ServerHttpRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeaders().getFirst(header);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        // Run before JwtAuthenticationFilter (which is ~-1)
        return -2;
    }
}