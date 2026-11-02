package com.adityakumar.urlshortener.service;

import com.adityakumar.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1&r=2#frag",
            "HTTPS://Example.com/Path",
            "https://sub.example.co.in:8443/a/b"})
    void acceptsNormalWebUrls(String url) {
        assertEquals(url, UrlValidator.validate(url));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("https://example.com", UrlValidator.validate("  https://example.com  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "ftp://example.com/file",
            "file:///etc/passwd",
            "data:text/html;base64,AAAA",
            "example.com",
            "//example.com",
            "https://",
            "https://exa mple.com",
            "not a url"})
    void rejectsAnythingThatIsNotAnAbsoluteHttpUrl(String url) {
        assertThrows(InvalidUrlException.class, () -> UrlValidator.validate(url));
    }

    @Test
    void rejectsBlankAndOverlongUrls() {
        assertThrows(InvalidUrlException.class, () -> UrlValidator.validate("   "));
        assertThrows(InvalidUrlException.class, () -> UrlValidator.validate(null));
        String tooLong = "https://example.com/" + "a".repeat(UrlValidator.MAX_LENGTH);
        assertThrows(InvalidUrlException.class, () -> UrlValidator.validate(tooLong));
    }
}
