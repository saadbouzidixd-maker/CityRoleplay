package dev.cityroleplay.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatsTest {

    @Test
    void formatsDuration() {
        assertEquals("0s", Formats.duration(0));
        assertEquals("1m 5s", Formats.duration(65));
        assertEquals("1h 1m 1s", Formats.duration(3661));
        assertEquals("2d 0h 0m 0s", Formats.duration(172800));
    }

    @Test
    void formatsMoneyWithPrecision() {
        assertEquals("$12.35", Formats.money(12.345, 2, "$"));
        assertEquals("$0.00", Formats.money(0, 2, "$"));
    }

    @Test
    void validatesAmounts() {
        assertTrue(Formats.isFinitePositive(1));
        assertFalse(Formats.isFinitePositive(0));
        assertFalse(Formats.isFinitePositive(-1));
        assertFalse(Formats.isFinitePositive(Double.NaN));
        assertTrue(Formats.isFiniteNonNegative(0));
    }
}
