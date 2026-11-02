package com.adityakumar.urlshortener.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Used when Redis is not enabled. Counts live in this JVM only, so it does not work across several instances. */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Counter(long bucket, int count) {
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitDecision check(String key, int limit, Duration window) {
        long windowMillis = window.toMillis();
        long now = clock.millis();
        long bucket = now / windowMillis;

        Counter updated = counters.compute(key, (k, current) ->
                current == null || current.bucket() != bucket
                        ? new Counter(bucket, 1)
                        : new Counter(bucket, current.count() + 1));

        if (counters.size() > CLEANUP_THRESHOLD) {
            counters.values().removeIf(c -> c.bucket() != bucket);
        }

        if (updated.count() > limit) {
            long retryAfterMillis = (bucket + 1) * windowMillis - now;
            return RateLimitDecision.deny(Math.max(1, (retryAfterMillis + 999) / 1000));
        }
        return RateLimitDecision.allow(limit - updated.count());
    }
}
