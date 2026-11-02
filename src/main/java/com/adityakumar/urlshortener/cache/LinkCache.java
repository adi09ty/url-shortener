package com.adityakumar.urlshortener.cache;

import java.time.Duration;
import java.util.Optional;

/** Cache-aside store for hot redirect lookups. A miss is always safe: the database is the source of truth. */
public interface LinkCache {

    Optional<CachedLink> get(String shortCode);

    void put(String shortCode, CachedLink link, Duration ttl);

    void evict(String shortCode);
}
