package com.adityakumar.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Random 8-character codes from lowercase letters and digits (36^8, about 2.8 trillion combinations).
 * Random rather than sequential so codes cannot be guessed or enumerated.
 * Lowercase only, so uniqueness behaves the same on case-insensitive MySQL collations.
 */
@Component
public class ShortCodeGenerator {

    static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    static final int LENGTH = 8;

    private final Random random;

    public ShortCodeGenerator() {
        this(new SecureRandom());
    }

    ShortCodeGenerator(Random random) {
        this.random = random;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
