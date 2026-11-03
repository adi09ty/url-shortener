package com.adityakumar.urlshortener.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * A tiny circuit breaker shared by the Redis cache and rate limiter. After one failure, Redis is skipped
 * for a short pause so requests go straight to the database instead of each paying for a failed attempt.
 * After the pause the next request tries Redis again, and a success closes the breaker.
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisAvailability {

    private static final Logger log = LoggerFactory.getLogger(RedisAvailability.class);

    static final Duration PAUSE = Duration.ofSeconds(10);

    private final Clock clock;
    private volatile long skipUntilMillis = 0;

    public RedisAvailability(Clock clock) {
        this.clock = clock;
    }

    public boolean isAvailable() {
        return clock.millis() >= skipUntilMillis;
    }

    public void recordFailure(RuntimeException cause) {
        boolean wasAvailable = isAvailable();
        skipUntilMillis = clock.millis() + PAUSE.toMillis();
        if (wasAvailable) {
            log.warn("Redis unavailable ({}). Skipping it for {}s and using the database.",
                    cause.getMessage(), PAUSE.toSeconds());
        }
    }

    public void recordSuccess() {
        if (skipUntilMillis != 0) {
            skipUntilMillis = 0;
            log.info("Redis is available again.");
        }
    }
}
