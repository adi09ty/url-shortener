package com.adityakumar.urlshortener.exception;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String shortCode) {
        super("No link found for code: " + shortCode);
    }
}
