package com.adityakumar.urlshortener.dto;

import com.adityakumar.urlshortener.domain.Link;

import java.time.LocalDateTime;

public record LinkResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        boolean expired,
        long clickCount,
        LocalDateTime lastClickedAt) {

    public static LinkResponse from(Link link, String baseUrl, LocalDateTime now) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new LinkResponse(link.getShortCode(), base + "/" + link.getShortCode(), link.getOriginalUrl(),
                link.getCreatedAt(), link.getExpiresAt(), link.isExpired(now), link.getClickCount(),
                link.getLastClickedAt());
    }
}
