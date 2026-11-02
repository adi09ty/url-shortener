package com.adityakumar.urlshortener;

import com.adityakumar.urlshortener.cache.LinkCache;
import com.adityakumar.urlshortener.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole stack (controller, service, JPA, H2, in-memory cache) behind MockMvc, with a controllable clock. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UrlShortenerApiTest.TestClockConfig.class)
@TestPropertySource(properties = {
        "app.rate-limit.create-per-window=10000",
        "app.rate-limit.redirect-per-window=10000"})
class UrlShortenerApiTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-03-10T08:00:00Z"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired LinkCache cache;

    private static String json(String url, String alias, Integer days) {
        StringBuilder body = new StringBuilder("{\"url\":\"" + url + "\"");
        if (alias != null) {
            body.append(",\"alias\":\"").append(alias).append("\"");
        }
        if (days != null) {
            body.append(",\"expiresInDays\":").append(days);
        }
        return body.append("}").toString();
    }

    private String createLink(String url, String alias, Integer days) throws Exception {
        String body = mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json(url, alias, days)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.shortCode");
    }

    private static String unique() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void createReturnsShortLinkWithLocationHeader() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json("https://example.com/a", null, null)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.shortCode").value(org.hamcrest.Matchers.matchesPattern("[a-z0-9]{8}")))
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8080/")))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/a"))
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void redirectSendsA302WithoutCachingAndCountsTheClick() throws Exception {
        String code = createLink("https://example.com/target", null, null);

        mvc.perform(get("/" + code).header("Referer", "https://news.example").header("User-Agent", "JUnit"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"))
                .andExpect(header().string("Cache-Control", "no-store"));

        mvc.perform(get("/api/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1))
                .andExpect(jsonPath("$.lastClickedAt").exists());
    }

    @Test
    void firstRedirectFillsTheCacheAndLaterOnesStillCount() throws Exception {
        String code = createLink("https://example.com/cached", null, null);
        assertTrue(cache.get(code).isEmpty(), "nothing is cached before the first redirect");

        mvc.perform(get("/" + code)).andExpect(status().isFound());
        assertTrue(cache.get(code).isPresent(), "the first redirect should fill the cache");

        mvc.perform(get("/" + code)).andExpect(status().isFound());
        mvc.perform(get("/" + code)).andExpect(status().isFound());

        mvc.perform(get("/api/links/" + code)).andExpect(jsonPath("$.clickCount").value(3));
    }

    @Test
    void unknownCodeIs404() throws Exception {
        mvc.perform(get("/doesnotexist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/links/doesnotexist")).andExpect(status().isNotFound());
    }

    @Test
    void customAliasWorksCaseInsensitivelyAndCannotBeReused() throws Exception {
        String alias = unique();
        createLink("https://example.com/alias", alias.toUpperCase(), null);

        mvc.perform(get("/" + alias.toUpperCase())).andExpect(status().isFound());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json("https://example.com/other", alias, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void reservedAliasIsRejected() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json("https://example.com", "api", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void badInputIsRejectedWith400() throws Exception {
        for (String bad : new String[]{"javascript:alert(1)", "ftp://example.com", "example.com", "not a url"}) {
            mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json(bad, null, null)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json("https://example.com", "a b", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.alias").exists());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json("https://example.com", null, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expiresInDays").exists());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linkStopsRedirectingAfterItExpires() throws Exception {
        String code = createLink("https://example.com/temp", null, 1);

        mvc.perform(get("/" + code)).andExpect(status().isFound());
        mvc.perform(get("/api/links/" + code)).andExpect(jsonPath("$.expired").value(false));

        clock.advance(Duration.ofDays(2));

        mvc.perform(get("/" + code)).andExpect(status().isGone());
        mvc.perform(get("/api/links/" + code)).andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    void analyticsReportsClicksPerDayIncludingZeroDays() throws Exception {
        String code = createLink("https://example.com/stats", null, null);
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/" + code)).andExpect(status().isFound());
        }

        mvc.perform(get("/api/links/" + code + "/analytics?days=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.clicksByDay.length()").value(3))
                .andExpect(jsonPath("$.clicksByDay[2].clicks").value(3))
                .andExpect(jsonPath("$.clicksByDay[0].clicks").value(0));
    }

    @Test
    void analyticsRejectsAnOutOfRangeNumberOfDays() throws Exception {
        String code = createLink("https://example.com/stats2", null, null);
        mvc.perform(get("/api/links/" + code + "/analytics?days=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/links/" + code + "/analytics?days=500")).andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unknownApiPathIs404() throws Exception {
        mvc.perform(get("/api/nope")).andExpect(status().isNotFound());
    }
}
