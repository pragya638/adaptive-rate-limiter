package com.example.ratelimiter.history;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.example.ratelimiter.adaptive.AdaptiveDecision;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.history.DashboardHistoryDto.AnomalyRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.DecisionRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.LimitRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.TrafficRecord;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

/**
 * Records completed windows and adaptive evaluations and exposes bounded,
 * read-only snapshots to the dashboard.
 */
@Service
public class DashboardHistoryService {

    private static final String STORAGE_MEMORY = "MEMORY";

    private final DashboardHistoryStore store;
    private final DashboardHistoryProperties properties;
    private final Clock clock;

    public DashboardHistoryService(DashboardHistoryStore store, DashboardHistoryProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordTrafficWindow(TrafficMetricsDto window) {
        if (window == null) {
            throw new IllegalArgumentException("window cannot be null");
        }
        store.recordTraffic(new TrafficRecord(
                window.getWindowSequence(),
                window.getWindowStart(),
                window.getWindowEnd(),
                window.getWindowRequestCount(),
                window.getWindowAllowedRequests(),
                window.getWindowRejectedRequests(),
                window.getClientErrorRate(),
                window.getCurrentRequestRate(),
                window.getEwmaRequestRate(),
                window.getErrorRate()));
    }

    public void recordEvaluation(TrafficMetricsDto window, AnomalyAssessment anomaly,
            AdaptiveDecision decision, int appliedLimit) {
        if (window == null || anomaly == null || decision == null) {
            throw new IllegalArgumentException("evaluation history inputs cannot be null");
        }
        Instant evaluatedAt = decision.getEvaluatedAt();
        String action = decision.getAction() == null ? null : decision.getAction().name();

        store.recordEvaluation(
                new AnomalyRecord(
                        window.getWindowSequence(),
                        evaluatedAt,
                        anomaly.isAvailable(),
                        anomaly.getIsAnomaly(),
                        anomaly.getAnomalyScore(),
                        anomaly.getPrediction(),
                        anomaly.getModel()),
                new DecisionRecord(
                        window.getWindowSequence(),
                        evaluatedAt,
                        action,
                        decision.getCurrentLimit(),
                        decision.getProposedLimit(),
                        appliedLimit,
                        decision.getReasonCode(),
                        decision.getExplanation()),
                new LimitRecord(
                        window.getWindowSequence(),
                        evaluatedAt,
                        decision.getCurrentLimit(),
                        appliedLimit,
                        action));
    }

    public DashboardHistoryDto snapshot() {
        DashboardHistoryStore.Snapshot snapshot = store.snapshot();
        return new DashboardHistoryDto(
                clock.instant(),
                STORAGE_MEMORY,
                false,
                properties.getMaxRecords(),
                snapshot.traffic(),
                snapshot.anomalies(),
                snapshot.decisions(),
                snapshot.limits());
    }
}
