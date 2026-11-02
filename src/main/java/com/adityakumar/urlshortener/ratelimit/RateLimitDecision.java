package com.adityakumar.urlshortener.ratelimit;

public record RateLimitDecision(boolean allowed, int remaining, long retryAfterSeconds) {

    public static RateLimitDecision allow(int remaining) {
        return new RateLimitDecision(true, remaining, 0);
    }

    public static RateLimitDecision deny(long retryAfterSeconds) {
        return new RateLimitDecision(false, 0, retryAfterSeconds);
    }
}
