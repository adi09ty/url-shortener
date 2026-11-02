package com.adityakumar.urlshortener.ratelimit;

import com.adityakumar.urlshortener.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    // Starts exactly on a window boundary so the arithmetic in the assertions is easy to follow.
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        for (int i = 1; i <= 3; i++) {
            RateLimitDecision decision = limiter.check("client", 3, WINDOW);
            assertTrue(decision.allowed());
            assertEquals(3 - i, decision.remaining());
        }
        RateLimitDecision blocked = limiter.check("client", 3, WINDOW);
        assertFalse(blocked.allowed());
        assertEquals(60, blocked.retryAfterSeconds());
    }

    @Test
    void retryAfterShrinksAsTheWindowRunsDown() {
        limiter.check("client", 1, WINDOW);
        clock.advance(Duration.ofSeconds(45));
        RateLimitDecision blocked = limiter.check("client", 1, WINDOW);
        assertFalse(blocked.allowed());
        assertEquals(15, blocked.retryAfterSeconds());
    }

    @Test
    void allowsAgainInTheNextWindow() {
        limiter.check("client", 1, WINDOW);
        assertFalse(limiter.check("client", 1, WINDOW).allowed());
        clock.advance(WINDOW);
        assertTrue(limiter.check("client", 1, WINDOW).allowed());
    }

    @Test
    void clientsAreCountedIndependently() {
        assertTrue(limiter.check("a", 1, WINDOW).allowed());
        assertFalse(limiter.check("a", 1, WINDOW).allowed());
        assertTrue(limiter.check("b", 1, WINDOW).allowed());
    }
}
