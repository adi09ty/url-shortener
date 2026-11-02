package com.adityakumar.urlshortener.service;

import com.adityakumar.urlshortener.exception.InvalidUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Only absolute http(s) URLs with a host are accepted, which rules out javascript:, data:, file: and similar. */
final class UrlValidator {

    static final int MAX_LENGTH = 2048;

    private UrlValidator() {
    }

    static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("URL must not be empty");
        }
        String url = raw.trim();
        if (url.length() > MAX_LENGTH) {
            throw new InvalidUrlException("URL must be at most " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not valid: " + e.getReason());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidUrlException("URL must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL must contain a host name");
        }
        return url;
    }
}
