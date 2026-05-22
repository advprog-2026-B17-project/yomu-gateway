package com.yomu.gateway.filter;

import com.yomu.gateway.config.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimitFilterTest {

    private RateLimitConfig rateLimitConfig;
    private LoginRateLimitFilter filter;
    private GatewayFilterChain mockChain;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
        rateLimitConfig.setLoginAttempts(5);
        filter = new LoginRateLimitFilter(rateLimitConfig);
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldAllowRequestWhenUnderLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, mockChain).block();

        verify(mockChain).filter(exchange);
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldAllowMultipleRequestsUnderLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        for (int i = 0; i < 5; i++) {
            filter.filter(exchange, mockChain).block();
        }

        verify(mockChain, times(5)).filter(exchange);
    }

    @Test
    void shouldBlockRequestWhenLimitExceeded() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        for (int i = 0; i < 5; i++) {
            filter.filter(exchange, mockChain).block();
        }

        reset(mockChain);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, mockChain).block();

        verify(mockChain, never()).filter(exchange);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldNotRateLimitNonLoginPaths() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/register")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        for (int i = 0; i < 10; i++) {
            filter.filter(exchange, mockChain).block();
        }

        verify(mockChain, times(10)).filter(exchange);
    }

    @Test
    void shouldExtractIpFromXForwardedForHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.50, 70.41.3.18, 150.172.238.178")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, mockChain).block();

        verify(mockChain).filter(exchange);
    }

    @Test
    void shouldReturnRateLimitHeadersWhenAllowed() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, mockChain).block();

        assertNotNull(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        assertNotNull(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Reset"));
    }

    @Test
    void shouldReturn429WithRetryAfterHeaderWhenBlocked() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        for (int i = 0; i < 5; i++) {
            filter.filter(exchange, mockChain).block();
        }

        reset(mockChain);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, mockChain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }
}