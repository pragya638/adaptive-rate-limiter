package com.example.ratelimiter.history;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.ToLongFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.ratelimiter.history.DashboardHistoryDto.AnomalyRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.DecisionRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.LimitRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.TrafficRecord;

/**
 * Thread-safe, bounded, in-memory history of dashboard-facing observations.
 *
 * <p>Each series is kept in ascending window-sequence order and is trimmed from
 * the oldest entry when its configured bound is reached. Duplicate or older
 * sequences are ignored so a retry or a late listener cannot corrupt the
 * timeline. No persistence layer is involved.
 */
@Component
public class DashboardHistoryStore {

    private final int maxRecords;
    private final Deque<TrafficRecord> traffic = new ArrayDeque<>();
    private final Deque<AnomalyRecord> anomalies = new ArrayDeque<>();
    private final Deque<DecisionRecord> decisions = new ArrayDeque<>();
    private final Deque<LimitRecord> limits = new ArrayDeque<>();

    @Autowired
    public DashboardHistoryStore(DashboardHistoryProperties properties) {
        this(properties.getMaxRecords());
    }

    public DashboardHistoryStore(int maxRecords) {
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be at least 1");
        }
        this.maxRecords = maxRecords;
    }

    public synchronized void recordTraffic(TrafficRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("traffic record cannot be null");
        }
        append(traffic, record, TrafficRecord::windowSequence);
    }

    public synchronized void recordEvaluation(AnomalyRecord anomaly, DecisionRecord decision, LimitRecord limit) {
        if (anomaly == null || decision == null || limit == null) {
            throw new IllegalArgumentException("evaluation history records cannot be null");
        }
        append(anomalies, anomaly, AnomalyRecord::windowSequence);
        append(decisions, decision, DecisionRecord::windowSequence);
        append(limits, limit, LimitRecord::windowSequence);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                List.copyOf(traffic),
                List.copyOf(anomalies),
                List.copyOf(decisions),
                List.copyOf(limits));
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public synchronized void clear() {
        traffic.clear();
        anomalies.clear();
        decisions.clear();
        limits.clear();
    }

    private <T> void append(Deque<T> records, T record, ToLongFunction<T> sequence) {
        long nextSequence = sequence.applyAsLong(record);
        if (!records.isEmpty() && sequence.applyAsLong(records.peekLast()) >= nextSequence) {
            return;
        }
        records.addLast(record);
        while (records.size() > maxRecords) {
            records.removeFirst();
        }
    }

    public record Snapshot(
            List<TrafficRecord> traffic,
            List<AnomalyRecord> anomalies,
            List<DecisionRecord> decisions,
            List<LimitRecord> limits) {
    }
}
