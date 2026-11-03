package com.adityakumar.urlshortener.cache;

import com.adityakumar.urlshortener.config.RedisAvailability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache. Every call fails open: if Redis is down the call behaves as a cache miss
 * (and the shared {@link RedisAvailability} breaker stops further attempts for a short while),
 * so redirects keep working from the database instead of returning errors.
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisLinkCache implements LinkCache {

    private static final String PREFIX = "link:";

    private final StringRedisTemplate redis;
    private final RedisAvailability availability;

    public RedisLinkCache(StringRedisTemplate redis, RedisAvailability availability) {
        this.redis = redis;
        this.availability = availability;
    }

    @Override
    public Optional<CachedLink> get(String shortCode) {
        if (!availability.isAvailable()) {
            return Optional.empty();
        }
        try {
            String raw = redis.opsForValue().get(PREFIX + shortCode);
            availability.recordSuccess();
            return Optional.ofNullable(CachedLink.decode(raw));
        } catch (RuntimeException e) {
            availability.recordFailure(e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortCode, CachedLink link, Duration ttl) {
        if (!availability.isAvailable()) {
            return;
        }
        try {
            redis.opsForValue().set(PREFIX + shortCode, link.encode(), ttl);
            availability.recordSuccess();
        } catch (RuntimeException e) {
            availability.recordFailure(e);
        }
    }

    @Override
    public void evict(String shortCode) {
        if (!availability.isAvailable()) {
            return;
        }
        try {
            redis.delete(PREFIX + shortCode);
            availability.recordSuccess();
        } catch (RuntimeException e) {
            availability.recordFailure(e);
        }
    }
}
