package com.adityakumar.urlshortener.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @NotBlank @Size(max = 2048) String url,
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "must be 3-30 letters, digits, '-' or '_'") String alias,
        @Min(1) @Max(3650) Integer expiresInDays) {
}
