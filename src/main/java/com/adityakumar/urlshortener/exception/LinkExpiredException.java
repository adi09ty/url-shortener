package com.adityakumar.urlshortener.exception;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String shortCode) {
        super("This link has expired: " + shortCode);
    }
}
