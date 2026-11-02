package com.adityakumar.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** One shared UTC clock. Tests replace it to control time (expiry, rate-limit windows). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
