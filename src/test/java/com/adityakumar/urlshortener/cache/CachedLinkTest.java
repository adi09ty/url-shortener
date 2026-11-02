package com.adityakumar.urlshortener.cache;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedLinkTest {

    @Test
    void roundTripWithoutExpiry() {
        CachedLink original = new CachedLink(42L, "https://example.com/a?b=c&d=e", null);
        assertEquals(original, CachedLink.decode(original.encode()));
    }

    @Test
    void roundTripWithExpiry() {
        CachedLink original = new CachedLink(7L, "https://example.com", LocalDateTime.of(2026, 3, 1, 12, 30, 15));
        assertEquals(original, CachedLink.decode(original.encode()));
    }

    @Test
    void urlContainingPipesAndColonsSurvives() {
        CachedLink original = new CachedLink(1L, "https://example.com/a|b:c;d", null);
        assertEquals(original, CachedLink.decode(original.encode()));
    }

    @Test
    void malformedEntriesDecodeToNullSoTheyActAsCacheMisses() {
        assertNull(CachedLink.decode(null));
        assertNull(CachedLink.decode(""));
        assertNull(CachedLink.decode("only-one-part"));
        assertNull(CachedLink.decode("abc\n1\nhttps://example.com"));
        assertNull(CachedLink.decode("0\nnot-a-number\nhttps://example.com"));
    }

    @Test
    void expiryIsInclusiveOfTheExpiryInstant() {
        LocalDateTime expiry = LocalDateTime.of(2026, 1, 1, 0, 0);
        CachedLink link = new CachedLink(1L, "https://example.com", expiry);
        assertFalse(link.isExpired(expiry.minusSeconds(1)));
        assertTrue(link.isExpired(expiry));
        assertFalse(new CachedLink(1L, "https://example.com", null).isExpired(expiry.plusYears(50)));
    }
}
