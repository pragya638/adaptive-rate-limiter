import type {
  AdaptiveStatus,
  DashboardHistory,
  HealthStatus,
  MlHealth,
  SystemStatus,
  TrafficMetrics,
} from "../api/types";

export function metricsFixture(
  overrides: Partial<TrafficMetrics> = {},
): TrafficMetrics {
  return {
    totalRequests: 120,
    allowedRequests: 110,
    rejectedRequests: 10,
    windowRequestCount: 12,
    windowAllowedRequests: 11,
    windowRejectedRequests: 1,
    errorRate: 0.08,
    clientErrorRate: 0.02,
    currentRequestRate: 4,
    ewmaRequestRate: 3.5,
    observationTimestamp: "2026-01-01T10:00:30Z",
    windowSequence: 4,
    windowStart: "2026-01-01T10:00:00Z",
    windowEnd: "2026-01-01T10:00:30Z",
    windowDurationSeconds: 30,
    ...overrides,
  };
}

export function statusFixture(
  overrides: Partial<AdaptiveStatus> = {},
): AdaptiveStatus {
  return {
    enabled: true,
    mode: "ADAPTIVE",
    currentLimit: 40,
    minLimit: 1,
    maxLimit: 1000,
    initialLimit: 5,
    lastEvaluationAt: "2026-01-01T10:00:31Z",
    lastAdaptationAt: "2026-01-01T10:00:31Z",
    latestAnomaly: {
      available: true,
      isAnomaly: false,
      anomalyScore: -0.12,
      prediction: "normal",
      model: "IsolationForest",
    },
    latestFeatureVector: {
      requestRate: 0.4,
      errorRate: 0.02,
      burstRatio: 0.11,
      requestCount: 12,
    },
    trafficWindow: {
      sequence: 4,
      start: "2026-01-01T10:00:00Z",
      end: "2026-01-01T10:00:30Z",
      durationSeconds: 30,
      requestCount: 12,
      allowedRequests: 11,
      rejectedRequests: 1,
      clientErrorRate: 0.02,
      requestRate: 4,
      ewmaRequestRate: 3.5,
    },
    lastDecision: {
      action: "INCREASE_LIMIT",
      previousLimit: 30,
      appliedLimit: 40,
      reasonCode: "LOW_LOAD",
      explanation: "Traffic is below the adaptive target range.",
      evaluatedAt: "2026-01-01T10:00:31Z",
    },
    ...overrides,
  };
}

export function healthFixture(
  overrides: Partial<HealthStatus> = {},
): HealthStatus {
  return {
    status: "UP",
    service: "adaptive-rate-limiter",
    timestamp: "2026-01-01T10:00:30Z",
    ...overrides,
  };
}

export function mlHealthFixture(overrides: Partial<MlHealth> = {}): MlHealth {
  return {
    status: "ok",
    model_ready: true,
    model: "IsolationForest",
    version: "0.4.0",
    ...overrides,
  };
}

export function historyFixture(
  overrides: Partial<DashboardHistory> = {},
): DashboardHistory {
  return {
    generatedAt: "2026-01-01T10:00:32Z",
    storage: "MEMORY",
    survivesRestart: false,
    maxRecords: 120,
    traffic: [
      {
        windowSequence: 4,
        windowStart: "2026-01-01T10:00:00Z",
        windowEnd: "2026-01-01T10:00:30Z",
        requestCount: 12,
        allowedRequests: 11,
        rejectedRequests: 1,
        clientErrorRate: 0.02,
        requestRate: 4,
        ewmaRequestRate: 3.5,
        errorRate: 0.08,
      },
    ],
    anomalies: [
      {
        windowSequence: 4,
        evaluatedAt: "2026-01-01T10:00:31Z",
        available: true,
        isAnomaly: false,
        anomalyScore: -0.12,
        prediction: "normal",
        model: "IsolationForest",
      },
    ],
    decisions: [
      {
        windowSequence: 4,
        evaluatedAt: "2026-01-01T10:00:31Z",
        action: "INCREASE_LIMIT",
        previousLimit: 30,
        proposedLimit: 40,
        appliedLimit: 40,
        reasonCode: "LOW_LOAD",
        explanation: "Traffic is below the adaptive target range.",
      },
    ],
    limits: [
      {
        windowSequence: 4,
        evaluatedAt: "2026-01-01T10:00:31Z",
        previousLimit: 30,
        limit: 40,
        action: "INCREASE_LIMIT",
      },
    ],
    ...overrides,
  };
}

export function systemStatusFixture(
  overrides: Partial<SystemStatus> = {},
): SystemStatus {
  return {
    timestamp: "2026-01-01T10:00:32Z",
    backend: {
      status: "UP",
      service: "adaptive-rate-limiter",
    },
    rateLimiter: {
      enabled: true,
      mode: "ADAPTIVE",
      configuredMaxRequests: 5,
      activeLimit: 40,
      windowMillis: 30_000,
      windowSeconds: 30,
      store: "MEMORY",
      includePaths: ["/api/**"],
      excludePaths: ["/api/health", "/api/metrics", "/api/adaptive/status", "/api/system-status"],
    },
    store: {
      type: "MEMORY",
      available: true,
      verified: true,
      status: "ready",
      detail: "In-process counter store; no external dependency.",
      fallbackMode: null,
    },
    monitoring: {
      schedulerStatus: "scheduled",
      observationIntervalSeconds: 30,
      ewmaAlpha: 0.3,
      pendingWindows: 0,
      lastCompletedAt: "2026-01-01T10:00:30Z",
      latestWindow: statusFixture().trafficWindow,
    },
    ml: {
      enabled: true,
      checkedAt: "2026-01-01T10:00:32Z",
      available: true,
      modelReady: true,
      status: "ok",
      model: "IsolationForest",
      version: "0.4.0",
      latencyMs: 12,
      lastSuccessfulEvaluationAt: "2026-01-01T10:00:31Z",
      lastFailureAt: null,
      lastFailureDetail: null,
      detail: null,
      modelInfo: {
        model: "IsolationForest",
        contamination: 0.05,
        nEstimators: 200,
        randomState: 42,
        featureNames: ["request_rate", "error_rate", "burst_ratio", "request_count"],
        observationIntervalSeconds: 30,
        fitted: true,
        trainingSamples: 2000,
      },
    },
    adaptive: statusFixture(),
    ...overrides,
  };
}
