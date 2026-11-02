package com.adityakumar.urlshortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void codesHaveTheExpectedLengthAndOnlyLowercaseLettersAndDigits() {
        for (int i = 0; i < 200; i++) {
            String code = generator.generate();
            assertEquals(ShortCodeGenerator.LENGTH, code.length());
            assertTrue(code.matches("[a-z0-9]+"), "unexpected characters in " + code);
        }
    }

    @Test
    void codesAreNotRepeated() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            codes.add(generator.generate());
        }
        assertEquals(2000, codes.size());
    }
}
