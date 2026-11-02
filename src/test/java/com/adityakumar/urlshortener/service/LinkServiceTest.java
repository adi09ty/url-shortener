package com.adityakumar.urlshortener.service;

import com.adityakumar.urlshortener.cache.CachedLink;
import com.adityakumar.urlshortener.cache.LinkCache;
import com.adityakumar.urlshortener.config.AppProperties;
import com.adityakumar.urlshortener.domain.Link;
import com.adityakumar.urlshortener.dto.CreateLinkRequest;
import com.adityakumar.urlshortener.exception.AliasAlreadyExistsException;
import com.adityakumar.urlshortener.exception.LinkExpiredException;
import com.adityakumar.urlshortener.exception.LinkNotFoundException;
import com.adityakumar.urlshortener.repository.ClickEventRepository;
import com.adityakumar.urlshortener.repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-03-10T08:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock LinkRepository links;
    @Mock ClickEventRepository clickEvents;
    @Mock LinkCache cache;
    @Mock ShortCodeGenerator generator;

    private LinkService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://localhost:8080",
                new AppProperties.Cache(Duration.ofHours(1)),
                new AppProperties.RateLimit(20, 120, Duration.ofMinutes(1)));
        service = new LinkService(links, clickEvents, cache, generator, props, Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
    }

    private static Link link(long id, String code, LocalDateTime expiresAt) {
        Link link = new Link(code, "https://example.com/" + code, NOW.minusDays(1), expiresAt);
        ReflectionTestUtils.setField(link, "id", id);
        return link;
    }

    @Test
    void cacheHitServesTheRedirectWithoutReadingTheDatabase() {
        when(cache.get("abc")).thenReturn(Optional.of(new CachedLink(5L, "https://example.com/abc", null)));

        String target = service.resolve("abc", "https://ref.example", "JUnit");

        assertEquals("https://example.com/abc", target);
        verify(links, never()).findByShortCode(any());
        verify(links).recordClick(5L, NOW);
    }

    @Test
    void cacheMissLoadsFromTheDatabaseAndFillsTheCache() {
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(links.findByShortCode("abc")).thenReturn(Optional.of(link(5L, "abc", null)));

        service.resolve("abc", null, null);

        verify(cache).put(eq("abc"), any(CachedLink.class), eq(Duration.ofHours(1)));
        verify(links).recordClick(5L, NOW);
    }

    @Test
    void cacheLifetimeIsCappedByTheTimeLeftBeforeExpiry() {
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(links.findByShortCode("abc")).thenReturn(Optional.of(link(5L, "abc", NOW.plusMinutes(10))));

        service.resolve("abc", null, null);

        verify(cache).put(eq("abc"), any(CachedLink.class), eq(Duration.ofMinutes(10)));
    }

    @Test
    void expiredLinkIsRejectedNotCachedAndNotCounted() {
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(links.findByShortCode("abc")).thenReturn(Optional.of(link(5L, "abc", NOW.minusSeconds(1))));

        assertThrows(LinkExpiredException.class, () -> service.resolve("abc", null, null));

        verify(cache, never()).put(any(), any(), any());
        verify(links, never()).recordClick(any(), any());
    }

    @Test
    void linkThatExpiredWhileSittingInTheCacheIsStillRejected() {
        when(cache.get("abc")).thenReturn(Optional.of(new CachedLink(5L, "https://example.com", NOW.minusSeconds(5))));

        assertThrows(LinkExpiredException.class, () -> service.resolve("abc", null, null));
        verify(links, never()).recordClick(any(), any());
    }

    @Test
    void unknownCodeIsNotFound() {
        when(cache.get("nope")).thenReturn(Optional.empty());
        when(links.findByShortCode("nope")).thenReturn(Optional.empty());

        assertThrows(LinkNotFoundException.class, () -> service.resolve("nope", null, null));
    }

    @Test
    void codesAreMatchedCaseInsensitively() {
        when(cache.get("abc")).thenReturn(Optional.of(new CachedLink(5L, "https://example.com", null)));

        service.resolve("AbC", null, null);

        verify(cache).get("abc");
    }

    @Test
    void aliasesAreStoredLowercase() {
        when(links.existsByShortCode("mylink")).thenReturn(false);
        when(links.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new CreateLinkRequest("https://example.com", "MyLink", null));

        assertEquals("mylink", response.shortCode());
        assertEquals("http://localhost:8080/mylink", response.shortUrl());
    }

    @Test
    void takenAndReservedAliasesAreRejected() {
        when(links.existsByShortCode("taken")).thenReturn(true);

        assertThrows(AliasAlreadyExistsException.class,
                () -> service.create(new CreateLinkRequest("https://example.com", "taken", null)));
        assertThrows(AliasAlreadyExistsException.class,
                () -> service.create(new CreateLinkRequest("https://example.com", "api", null)));
        verify(links, never()).save(any());
    }

    @Test
    void expiryIsCountedFromNow() {
        when(generator.generate()).thenReturn("abcd1234");
        when(links.existsByShortCode("abcd1234")).thenReturn(false);
        when(links.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new CreateLinkRequest("https://example.com", null, 3));

        assertEquals(NOW.plusDays(3), response.expiresAt());
    }

    @Test
    void generatedCodeIsRetriedWhenItCollides() {
        when(generator.generate()).thenReturn("taken111", "free2222");
        when(links.existsByShortCode("taken111")).thenReturn(true);
        when(links.existsByShortCode("free2222")).thenReturn(false);
        when(links.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new CreateLinkRequest("https://example.com", null, null));

        assertEquals("free2222", response.shortCode());
    }
}
