package com.adityakumar.urlshortener.ratelimit;

import com.adityakumar.urlshortener.config.RedisAvailability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Counts requests per key and time window in Redis, so the limit holds across all app instances.
 * INCR and PEXPIRE run in one Lua script, which Redis executes atomically: a crash can never leave
 * a counter without an expiry. Fails open (allows the request) while Redis is unavailable.
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final RedisAvailability availability;

    public RedisRateLimiter(StringRedisTemplate redis, Clock clock, RedisAvailability availability) {
        this.redis = redis;
        this.clock = clock;
        this.availability = availability;
    }

    @Override
    public RateLimitDecision check(String key, int limit, Duration window) {
        if (!availability.isAvailable()) {
            return RateLimitDecision.allow(limit);
        }
        long windowMillis = window.toMillis();
        long now = clock.millis();
        long bucket = now / windowMillis;
        String redisKey = "rl:" + key + ":" + bucket;
        try {
            Long count = redis.execute(INCREMENT_WITH_EXPIRY, List.of(redisKey), String.valueOf(windowMillis));
            availability.recordSuccess();
            if (count != null && count > limit) {
                long retryAfterMillis = (bucket + 1) * windowMillis - now;
                return RateLimitDecision.deny(Math.max(1, (retryAfterMillis + 999) / 1000));
            }
            return RateLimitDecision.allow(count == null ? limit : (int) (limit - count));
        } catch (RuntimeException e) {
            availability.recordFailure(e);
            return RateLimitDecision.allow(limit);
        }
    }
}
