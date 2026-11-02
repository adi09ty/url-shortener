package com.adityakumar.urlshortener.cache;

import com.adityakumar.urlshortener.domain.Link;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * The few fields the redirect path needs. Kept small so a cache entry is tiny.
 * Encoded as plain text ("expiresEpochSeconds\nlinkId\nurl") to avoid a JSON dependency;
 * a URL can never contain a raw newline, so the split is unambiguous.
 */
public record CachedLink(Long linkId, String originalUrl, LocalDateTime expiresAt) {

    private static final long NO_EXPIRY = 0L;

    public static CachedLink from(Link link) {
        return new CachedLink(link.getId(), link.getOriginalUrl(), link.getExpiresAt());
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public String encode() {
        long expiry = expiresAt == null ? NO_EXPIRY : expiresAt.toEpochSecond(ZoneOffset.UTC);
        return expiry + "\n" + linkId + "\n" + originalUrl;
    }

    /** Returns null for anything malformed, so a corrupt entry is treated as a cache miss. */
    public static CachedLink decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\n", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            long expiry = Long.parseLong(parts[0]);
            Long id = Long.valueOf(parts[1]);
            LocalDateTime expiresAt = expiry == NO_EXPIRY ? null : LocalDateTime.ofEpochSecond(expiry, 0, ZoneOffset.UTC);
            return new CachedLink(id, parts[2], expiresAt);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
