package com.example.ratelimiter.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EwmaCalculatorTest {

    @Test
    void firstSampleInitializes() {
        EwmaCalculator ewma = new EwmaCalculator(0.3);
        ewma.update(10.0);
        assertEquals(10.0, ewma.getSmoothed(), 1e-9);
    }

    @Test
    void knownSequence() {
        EwmaCalculator ewma = new EwmaCalculator(0.5);
        ewma.update(10.0);
        ewma.update(20.0);
        assertEquals(15.0, ewma.getSmoothed(), 1e-9);
    }

    @Test
    void zeroRequestIntervalsTreatedAsZero() {
        EwmaCalculator ewma = new EwmaCalculator(0.3);
        ewma.update(5.0);
        ewma.update(0.0);
        assertEquals(3.5, ewma.getSmoothed(), 1e-9);
    }

    @Test
    void alphaValidation() {
        assertThrows(IllegalArgumentException.class, () -> new EwmaCalculator(0.0));
        assertThrows(IllegalArgumentException.class, () -> new EwmaCalculator(1.5));
    }

    @Test
    void alphaBoundaryValidation() {
        assertThrows(IllegalArgumentException.class, () -> new EwmaCalculator(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new EwmaCalculator(0.0));
        assertThrows(IllegalArgumentException.class, () -> new EwmaCalculator(2.0));
        EwmaCalculator ewma = new EwmaCalculator(1.0);
        ewma.update(5.0);
        ewma.update(7.0);
        assertEquals(7.0, ewma.getSmoothed(), 1e-9);
    }
}