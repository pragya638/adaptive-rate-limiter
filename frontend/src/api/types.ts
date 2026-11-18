export type AdaptiveMode = "STATIC" | "ADAPTIVE";

export type DecisionAction =
  | "INCREASE_LIMIT"
  | "DECREASE_LIMIT"
  | "KEEP_LIMIT";

export interface DemoResponse {
  status: number;
  statusText: string;
  ok: boolean;
  body: string;
  contentType: string | null;
  latencyMs: number;
  rateLimitLimit: string | null;
  rateLimitRemaining: string | null;
  retryAfter: string | null;
}

export interface HealthStatus {
  status: string;
  service: string;
  timestamp: string;
}

export interface TrafficMetrics {
  totalRequests: number;
  allowedRequests: number;
  rejectedRequests: number;
  windowRequestCount: number;
  windowAllowedRequests: number;
  windowRejectedRequests: number;
  errorRate: number;
  clientErrorRate: number;
  currentRequestRate: number;
  ewmaRequestRate: number;
  observationTimestamp: string | null;
  windowSequence: number;
  windowStart: string | null;
  windowEnd: string | null;
  windowDurationSeconds: number;
}

export interface AnomalyStatus {
  available: boolean;
  isAnomaly: boolean | null;
  anomalyScore: number | null;
  prediction: string | null;
  model: string | null;
}

export interface FeatureVectorStatus {
  requestRate: number;
  errorRate: number;
  burstRatio: number;
  requestCount: number;
}

export interface TrafficWindowStatus {
  sequence: number;
  start: string | null;
  end: string | null;
  durationSeconds: number;
  requestCount: number;
  allowedRequests: number;
  rejectedRequests: number;
  clientErrorRate: number;
  requestRate: number;
  ewmaRequestRate: number;
}

export interface DecisionStatus {
  action: DecisionAction | null;
  previousLimit: number;
  appliedLimit: number;
  reasonCode: string | null;
  explanation: string | null;
  evaluatedAt: string | null;
}

export interface AdaptiveStatus {
  enabled: boolean;
  mode: string;
  currentLimit: number;
  minLimit: number;
  maxLimit: number;
  initialLimit: number;
  lastEvaluationAt: string | null;
  lastAdaptationAt: string | null;
  latestAnomaly: AnomalyStatus | null;
  latestFeatureVector: FeatureVectorStatus | null;
  trafficWindow: TrafficWindowStatus | null;
  lastDecision: DecisionStatus | null;
}

export interface MlHealth {
  status: "ok" | "degraded" | string;
  model_ready: boolean;
  model: string;
  version: string;
}

export interface MlModelInfo {
  model: string | null;
  contamination: number;
  nEstimators: number;
  randomState: number;
  featureNames: string[] | null;
  observationIntervalSeconds: number;
  fitted: boolean;
  trainingSamples: number;
}

export interface DashboardHistoryTraffic {
  windowSequence: number;
  windowStart: string | null;
  windowEnd: string | null;
  requestCount: number;
  allowedRequests: number;
  rejectedRequests: number;
  clientErrorRate: number;
  requestRate: number;
  ewmaRequestRate: number;
  errorRate: number;
}

export interface DashboardHistoryAnomaly {
  windowSequence: number;
  evaluatedAt: string | null;
  available: boolean;
  isAnomaly: boolean | null;
  anomalyScore: number | null;
  prediction: string | null;
  model: string | null;
}

export interface DashboardHistoryDecision {
  windowSequence: number;
  evaluatedAt: string | null;
  action: string | null;
  previousLimit: number;
  proposedLimit: number;
  appliedLimit: number;
  reasonCode: string | null;
  explanation: string | null;
}

export interface DashboardHistoryLimit {
  windowSequence: number;
  evaluatedAt: string | null;
  previousLimit: number;
  limit: number;
  action: string | null;
}

export interface DashboardHistory {
  generatedAt: string;
  storage: string;
  survivesRestart: boolean;
  maxRecords: number;
  traffic: DashboardHistoryTraffic[];
  anomalies: DashboardHistoryAnomaly[];
  decisions: DashboardHistoryDecision[];
  limits: DashboardHistoryLimit[];
}


export interface SystemStatus {
  timestamp: string;
  backend: {
    status: string;
    service: string;
  };
  rateLimiter: {
    enabled: boolean;
    mode: string;
    configuredMaxRequests: number;
    activeLimit: number;
    windowMillis: number;
    windowSeconds: number;
    store: string;
    includePaths: string[];
    excludePaths: string[];
  };
  store: {
    type: string;
    available: boolean;
    verified: boolean;
    status: string;
    detail: string | null;
    fallbackMode: string | null;
  };
  monitoring: {
    schedulerStatus: string;
    observationIntervalSeconds: number;
    ewmaAlpha: number;
    pendingWindows: number;
    lastCompletedAt: string | null;
    latestWindow: TrafficWindowStatus | null;
  };
  ml: {
    enabled: boolean;
    checkedAt: string | null;
    available: boolean;
    modelReady: boolean;
    status: string;
    model: string | null;
    version: string | null;
    latencyMs: number | null;
    lastSuccessfulEvaluationAt: string | null;
    lastFailureAt: string | null;
    lastFailureDetail: string | null;
    detail: string | null;
    modelInfo: MlModelInfo | null;
  };
  adaptive: AdaptiveStatus;
}
