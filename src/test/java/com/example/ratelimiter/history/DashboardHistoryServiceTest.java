package com.example.ratelimiter.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.AdaptiveDecision;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.DecisionAction;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

class DashboardHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:01:00Z");

    @Test
    void mapsCompletedWindowsAndEvaluationsIntoReadOnlyHistory() {
        DashboardHistoryProperties properties = new DashboardHistoryProperties();
        properties.setMaxRecords(10);
        DashboardHistoryService service = new DashboardHistoryService(
                new DashboardHistoryStore(properties), properties, Clock.fixed(NOW, ZoneOffset.UTC));

        service.recordTrafficWindow(window());
        service.recordEvaluation(
                window(),
                new AnomalyAssessment(true, 0.42, "anomaly", "IsolationForest"),
                new AdaptiveDecision(DecisionAction.INCREASE_LIMIT, 5, 15, "SUSTAINED_GROWTH",
                        "Traffic grew consistently.", Instant.parse("2026-01-01T00:00:31Z")),
                12);

        DashboardHistoryDto history = service.snapshot();

        assertThat(history.generatedAt()).isEqualTo(NOW);
        assertThat(history.storage()).isEqualTo("MEMORY");
        assertThat(history.survivesRestart()).isFalse();
        assertThat(history.maxRecords()).isEqualTo(10);
        assertThat(history.traffic()).singleElement().satisfies(record -> {
            assertThat(record.windowSequence()).isEqualTo(1);
            assertThat(record.requestCount()).isEqualTo(25);
            assertThat(record.clientErrorRate()).isEqualTo(0.08);
        });
        assertThat(history.anomalies()).singleElement().satisfies(record -> {
            assertThat(record.available()).isTrue();
            assertThat(record.isAnomaly()).isTrue();
            assertThat(record.anomalyScore()).isEqualTo(0.42);
        });
        assertThat(history.decisions()).singleElement().satisfies(record -> {
            assertThat(record.action()).isEqualTo("INCREASE_LIMIT");
            assertThat(record.previousLimit()).isEqualTo(5);
            assertThat(record.proposedLimit()).isEqualTo(15);
            assertThat(record.appliedLimit()).isEqualTo(12);
        });
        assertThat(history.limits()).singleElement().satisfies(record -> {
            assertThat(record.previousLimit()).isEqualTo(5);
            assertThat(record.limit()).isEqualTo(12);
            assertThat(record.action()).isEqualTo("INCREASE_LIMIT");
        });
    }

    private TrafficMetricsDto window() {
        return new TrafficMetricsDto(
                1000L, 990L, 10L, 0.01, 0.83, 0.8,
                Instant.parse("2026-01-01T00:00:30Z"),
                25L, 20L, 5L, 0.08, 1L,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:30Z"), 30.0);
    }
}
