package com.yomu.gateway.config;

import io.github.bucket4j.Bucket;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitConfig {

    private int loginAttempts = 5;
    private Duration loginWindow = Duration.ofMinutes(15);
    private String headerRateLimitRemaining = "X-RateLimit-Remaining";
    private String headerRateLimitReset = "X-RateLimit-Reset";
    private String headerRetryAfter = "Retry-After";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::createNewBucket);
    }

    private Bucket createNewBucket(String key) {
        return Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(
                        loginAttempts,
                        io.github.bucket4j.Refill.greedy(loginAttempts, loginWindow)
                ))
                .build();
    }

    public void resetBucket(String key) {
        buckets.remove(key);
    }

    public int getLoginAttempts() {
        return loginAttempts;
    }

    public void setLoginAttempts(int loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    public Duration getLoginWindow() {
        return loginWindow;
    }

    public void setLoginWindow(Duration loginWindow) {
        this.loginWindow = loginWindow;
    }

    public String getHeaderRateLimitRemaining() {
        return headerRateLimitRemaining;
    }

    public String getHeaderRateLimitReset() {
        return headerRateLimitReset;
    }

    public String getHeaderRetryAfter() {
        return headerRetryAfter;
    }
}
