package com.adityakumar.urlshortener.ratelimit;

import java.time.Duration;

/** Fixed-window limiter: at most {@code limit} calls per {@code window} for each key. */
public interface RateLimiter {

    RateLimitDecision check(String key, int limit, Duration window);
}
