package com.adityakumar.urlshortener.service;

import com.adityakumar.urlshortener.cache.CachedLink;
import com.adityakumar.urlshortener.cache.LinkCache;
import com.adityakumar.urlshortener.config.AppProperties;
import com.adityakumar.urlshortener.domain.ClickEvent;
import com.adityakumar.urlshortener.domain.Link;
import com.adityakumar.urlshortener.dto.AnalyticsResponse;
import com.adityakumar.urlshortener.dto.AnalyticsResponse.DailyClicks;
import com.adityakumar.urlshortener.dto.CreateLinkRequest;
import com.adityakumar.urlshortener.dto.LinkResponse;
import com.adityakumar.urlshortener.exception.AliasAlreadyExistsException;
import com.adityakumar.urlshortener.exception.LinkExpiredException;
import com.adityakumar.urlshortener.exception.LinkNotFoundException;
import com.adityakumar.urlshortener.repository.ClickEventRepository;
import com.adityakumar.urlshortener.repository.LinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class LinkService {

    private static final int MAX_CODE_ATTEMPTS = 5;

    /** Codes that would collide with real routes. */
    private static final Set<String> RESERVED = Set.of("api", "error", "actuator", "health", "static");

    private final LinkRepository links;
    private final ClickEventRepository clickEvents;
    private final LinkCache cache;
    private final ShortCodeGenerator codeGenerator;
    private final AppProperties properties;
    private final Clock clock;

    public LinkService(LinkRepository links, ClickEventRepository clickEvents, LinkCache cache,
                       ShortCodeGenerator codeGenerator, AppProperties properties, Clock clock) {
        this.links = links;
        this.clickEvents = clickEvents;
        this.cache = cache;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    public LinkResponse create(CreateLinkRequest request) {
        String url = UrlValidator.validate(request.url());
        LocalDateTime now = now();
        String code = hasText(request.alias()) ? reserveAlias(request.alias()) : generateUniqueCode();
        LocalDateTime expiresAt = request.expiresInDays() == null ? null : now.plusDays(request.expiresInDays());

        Link saved = links.save(new Link(code, url, now, expiresAt));
        return toResponse(saved, now);
    }

    @Transactional(readOnly = true)
    public LinkResponse getDetails(String rawCode) {
        return toResponse(findOrThrow(normalize(rawCode)), now());
    }

    /**
     * The redirect path. Cache-aside: try the cache, fall back to the database and fill the cache.
     * The click is recorded on every redirect, cached or not.
     */
    public String resolve(String rawCode, String referrer, String userAgent) {
        String code = normalize(rawCode);
        LocalDateTime now = now();

        CachedLink target = cache.get(code).orElse(null);
        if (target == null) {
            target = CachedLink.from(findOrThrow(code));
            cacheIfStillValid(code, target, now);
        }
        if (target.isExpired(now)) {
            throw new LinkExpiredException(code);
        }

        recordClick(target.linkId(), now, referrer, userAgent);
        return target.originalUrl();
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(String rawCode, int days) {
        Link link = findOrThrow(normalize(rawCode));
        LocalDate today = now().toLocalDate();
        LocalDate first = today.minusDays(days - 1L);

        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : clickEvents.countByDay(link.getId(), first.atStartOfDay())) {
            counts.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        List<DailyClicks> perDay = first.datesUntil(today.plusDays(1))
                .map(day -> new DailyClicks(day, counts.getOrDefault(day, 0L)))
                .toList();
        return new AnalyticsResponse(link.getShortCode(), link.getClickCount(), days, perDay);
    }

    private void recordClick(Long linkId, LocalDateTime now, String referrer, String userAgent) {
        links.recordClick(linkId, now);
        clickEvents.save(new ClickEvent(links.getReferenceById(linkId), now, referrer, userAgent));
    }

    /** Never cache longer than the link lives, and never cache a link that is already expired. */
    private void cacheIfStillValid(String code, CachedLink target, LocalDateTime now) {
        Duration ttl = properties.cache().ttl();
        if (target.expiresAt() != null) {
            Duration remaining = Duration.between(now, target.expiresAt());
            if (remaining.isZero() || remaining.isNegative()) {
                return;
            }
            ttl = remaining.compareTo(ttl) < 0 ? remaining : ttl;
        }
        cache.put(code, target, ttl);
    }

    private String reserveAlias(String alias) {
        String code = normalize(alias);
        if (RESERVED.contains(code) || links.existsByShortCode(code)) {
            throw new AliasAlreadyExistsException(code);
        }
        return code;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.generate();
            if (!links.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code");
    }

    private Link findOrThrow(String code) {
        return links.findByShortCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    }

    private LinkResponse toResponse(Link link, LocalDateTime now) {
        return LinkResponse.from(link, properties.baseUrl(), now);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String normalize(String code) {
        return code.toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
