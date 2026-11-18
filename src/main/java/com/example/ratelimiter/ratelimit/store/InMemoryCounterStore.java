package com.example.ratelimiter.ratelimit.store;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Original in-process counter implementation. Each key stores the current
 * window start and count, exactly like the pre-Redis limiters did.
 */
public class InMemoryCounterStore implements CounterStore {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public CounterIncrement increment(String clientKey, long windowStartEpochMillis, long windowMillis) {
        WindowCounter counter = counters.compute(clientKey, (key, current) -> {
            if (current == null || current.windowStartEpochMillis != windowStartEpochMillis) {
                return new WindowCounter(windowStartEpochMillis, 1);
            }
            return new WindowCounter(current.windowStartEpochMillis, current.count + 1);
        });
        return new CounterIncrement(counter.count, false);
    }

    @Override
    public void reset() {
        counters.clear();
    }

    private record WindowCounter(long windowStartEpochMillis, long count) {
    }
}
