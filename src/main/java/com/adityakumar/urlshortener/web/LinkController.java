package com.adityakumar.urlshortener.web;

import com.adityakumar.urlshortener.dto.AnalyticsResponse;
import com.adityakumar.urlshortener.dto.CreateLinkRequest;
import com.adityakumar.urlshortener.dto.LinkResponse;
import com.adityakumar.urlshortener.service.LinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService service;

    public LinkController(LinkService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        LinkResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/links/" + created.shortCode())).body(created);
    }

    @GetMapping("/{code}")
    public LinkResponse details(@PathVariable String code) {
        return service.getDetails(code);
    }

    @GetMapping("/{code}/analytics")
    public AnalyticsResponse analytics(@PathVariable String code,
                                       @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return service.analytics(code, days);
    }
}
