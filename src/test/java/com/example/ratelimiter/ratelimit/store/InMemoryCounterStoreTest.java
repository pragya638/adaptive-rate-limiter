package com.example.ratelimiter.ratelimit.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class InMemoryCounterStoreTest {

    @Test
    void incrementsWithinTheSameWindowAndResetsWhenWindowChanges() {
        InMemoryCounterStore store = new InMemoryCounterStore();

        assertThat(store.increment("client", 1_000, 500).count()).isEqualTo(1);
        assertThat(store.increment("client", 1_000, 500).count()).isEqualTo(2);
        assertThat(store.increment("client", 1_500, 500).count()).isEqualTo(1);
    }

    @Test
    void keepsClientsIndependent() {
        InMemoryCounterStore store = new InMemoryCounterStore();

        store.increment("a", 1_000, 500);
        store.increment("a", 1_000, 500);

        assertThat(store.increment("b", 1_000, 500).count()).isEqualTo(1);
    }

    @Test
    void isThreadSafeForConcurrentIncrements() throws Exception {
        InMemoryCounterStore store = new InMemoryCounterStore();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                tasks.add(() -> store.increment("client", 1_000, 500).count());
            }
            List<Future<Long>> results = executor.invokeAll(tasks);
            long max = results.stream().mapToLong(result -> {
                try {
                    return result.get();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).max().orElse(0);
            assertThat(max).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resetClearsAllCounters() {
        InMemoryCounterStore store = new InMemoryCounterStore();
        store.increment("client", 1_000, 500);

        store.reset();

        assertThat(store.increment("client", 1_000, 500).count()).isEqualTo(1);
    }
}
