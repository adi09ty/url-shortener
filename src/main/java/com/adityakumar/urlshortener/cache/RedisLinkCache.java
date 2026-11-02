package com.adityakumar.urlshortener.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache. Every call fails open: if Redis is down we log a warning and behave
 * as a cache miss, so redirects keep working (just slower) instead of returning errors.
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisLinkCache implements LinkCache {

    private static final Logger log = LoggerFactory.getLogger(RedisLinkCache.class);
    private static final String PREFIX = "link:";

    private final StringRedisTemplate redis;

    public RedisLinkCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<CachedLink> get(String shortCode) {
        try {
            return Optional.ofNullable(CachedLink.decode(redis.opsForValue().get(PREFIX + shortCode)));
        } catch (RuntimeException e) {
            log.warn("Redis read failed, falling back to the database: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortCode, CachedLink link, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + shortCode, link.encode(), ttl);
        } catch (RuntimeException e) {
            log.warn("Redis write failed, continuing without cache: {}", e.getMessage());
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redis.delete(PREFIX + shortCode);
        } catch (RuntimeException e) {
            log.warn("Redis delete failed: {}", e.getMessage());
        }
    }
}
