package com.adityakumar.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One row per redirect. Powers the per-day analytics. */
@Entity
@Table(name = "click_events", indexes = @Index(name = "idx_click_link_time", columnList = "link_id, clicked_at"))
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "link_id")
    private Link link;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    @Column(length = 512)
    private String referrer;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected ClickEvent() {
        // required by JPA
    }

    public ClickEvent(Link link, LocalDateTime clickedAt, String referrer, String userAgent) {
        this.link = link;
        this.clickedAt = clickedAt;
        this.referrer = truncate(referrer, 512);
        this.userAgent = truncate(userAgent, 255);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
