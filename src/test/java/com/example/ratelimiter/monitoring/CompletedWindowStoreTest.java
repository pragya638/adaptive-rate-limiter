package com.example.ratelimiter.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CompletedWindowStoreTest {

    @Test
    void publishesAndConsumesWindowsInSequenceOrder() {
        CompletedWindowStore store = new CompletedWindowStore();

        store.publish(window(1L));
        store.publish(window(2L));

        assertEquals(2, store.size());
        assertEquals(1L, store.peekFirst().getWindowSequence());
        assertEquals(1L, store.removeFirst().getWindowSequence());
        assertEquals(2L, store.removeFirst().getWindowSequence());
        assertNull(store.peekFirst());
    }

    @Test
    void peekDoesNotRemoveTheWindow() {
        CompletedWindowStore store = new CompletedWindowStore();
        TrafficMetricsDto window = window(7L);

        store.publish(window);

        assertSame(window, store.peekFirst());
        assertEquals(1, store.size());
    }

    @Test
    void dropsTheOldestWindowWhenTheBufferIsFull() {
        CompletedWindowStore store = new CompletedWindowStore(2);

        store.publish(window(1L));
        store.publish(window(2L));
        store.publish(window(3L));

        assertEquals(2, store.size());
        assertEquals(2L, store.removeFirst().getWindowSequence());
        assertEquals(3L, store.removeFirst().getWindowSequence());
    }

    private TrafficMetricsDto window(long sequence) {
        Instant start = Instant.parse("2026-11-04T00:00:00Z");
        return new TrafficMetricsDto(
                1000L, 990L, 10L, 0.01, 60.0, 55.0, start,
                25L, 20L, 5L, 0.08, sequence,
                start, start.plusSeconds(30), 30.0);
    }
}
