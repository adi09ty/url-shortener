package com.adityakumar.urlshortener.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Makes Redis failures cheap. By default the Redis client queues commands while it is disconnected
 * and waits for them to time out, so every request would hang when Redis goes down. Rejecting commands
 * while disconnected (plus short connect/command timeouts) lets the cache and rate limiter fail open
 * immediately and the redirect fall back to the database.
 */
@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);

    public static ClientOptions.Builder failFast(ClientOptions.Builder builder) {
        return builder
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    @Bean
    LettuceClientOptionsBuilderCustomizer failFastWhenRedisIsDown() {
        return RedisClientConfig::failFast;
    }
}
