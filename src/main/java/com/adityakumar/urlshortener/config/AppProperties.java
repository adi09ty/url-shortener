package com.adityakumar.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Typed view of the "app.*" settings in application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue Cache cache,
        @DefaultValue RateLimit rateLimit) {

    public record Cache(@DefaultValue("1h") Duration ttl) {
    }

    public record RateLimit(
            @DefaultValue("20") int createPerWindow,
            @DefaultValue("120") int redirectPerWindow,
            @DefaultValue("1m") Duration window) {
    }
}
