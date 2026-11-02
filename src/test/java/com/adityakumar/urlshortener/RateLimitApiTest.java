package com.adityakumar.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own application context (and so its own counters): tiny limits so the 429 path is easy to reach. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.create-per-window=3",
        "app.rate-limit.redirect-per-window=5",
        "app.rate-limit.window=1h"})
class RateLimitApiTest {

    @Autowired MockMvc mvc;

    @Test
    void creatingLinksAndRedirectsAreLimitedSeparately() throws Exception {
        String code = null;
        for (int i = 0; i < 3; i++) {
            String body = mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com/" + i + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)))
                    .andReturn().getResponse().getContentAsString();
            code = JsonPath.read(body, "$.shortCode");
        }

        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // Creation being blocked must not affect redirects, which have their own budget.
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/" + code)).andExpect(status().isFound());
        }
        mvc.perform(get("/" + code)).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void readingDetailsIsNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/links/whatever")).andExpect(status().isNotFound());
        }
    }
}
