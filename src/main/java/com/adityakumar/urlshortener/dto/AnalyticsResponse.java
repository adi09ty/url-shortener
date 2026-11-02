package com.adityakumar.urlshortener.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsResponse(String shortCode, long totalClicks, int days, List<DailyClicks> clicksByDay) {

    public record DailyClicks(LocalDate date, long clicks) {
    }
}
