package com.yomu.gateway.filter;

import com.yomu.gateway.config.RateLimitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimitFilterIntegrationTest {

    @Test
    void shouldAllowAllRequestsUntilLimitExceeded() {
        RateLimitConfig config = new RateLimitConfig();
        config.setLoginAttempts(3);

        LoginRateLimitFilter filter = new LoginRateLimitFilter(config);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();

        // Request 1 - allowed
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());

        // Request 2 - allowed
        exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());

        // Request 3 - allowed
        exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());

        // Request 4 - blocked
        exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldTrackSeparateIpsIndependently() {
        RateLimitConfig config = new RateLimitConfig();
        config.setLoginAttempts(2);

        LoginRateLimitFilter filter = new LoginRateLimitFilter(config);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // IP 1: 2 requests allowed
        MockServerHttpRequest ip1Request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                .build();

        MockServerWebExchange ip1Exchange = MockServerWebExchange.from(ip1Request);
        filter.filter(ip1Exchange, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ip1Exchange.getResponse().getStatusCode());

        ip1Exchange = MockServerWebExchange.from(ip1Request);
        filter.filter(ip1Exchange, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ip1Exchange.getResponse().getStatusCode());

        // IP 2: first request should be allowed (separate bucket)
        MockServerHttpRequest ip2Request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 8080))
                .build();
        MockServerWebExchange ip2Exchange = MockServerWebExchange.from(ip2Request);
        filter.filter(ip2Exchange, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ip2Exchange.getResponse().getStatusCode());
    }
}