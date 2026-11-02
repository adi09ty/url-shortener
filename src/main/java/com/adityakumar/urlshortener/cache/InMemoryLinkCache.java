package com.adityakumar.urlshortener.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Used when Redis is not enabled (local runs, tests). Same behaviour, but private to one instance. */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryLinkCache implements LinkCache {

    private record Entry(CachedLink link, Instant expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryLinkCache(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<CachedLink> get(String shortCode) {
        Entry entry = entries.get(shortCode);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(shortCode, entry);
            return Optional.empty();
        }
        return Optional.of(entry.link());
    }

    @Override
    public void put(String shortCode, CachedLink link, Duration ttl) {
        entries.put(shortCode, new Entry(link, clock.instant().plus(ttl)));
    }

    @Override
    public void evict(String shortCode) {
        entries.remove(shortCode);
    }
}
