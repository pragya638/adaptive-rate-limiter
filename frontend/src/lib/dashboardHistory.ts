import type {
  DashboardHistory,
  DashboardHistoryAnomaly,
  DashboardHistoryDecision,
  DashboardHistoryLimit,
  DashboardHistoryTraffic,
  DecisionAction,
  DecisionStatus,
} from "../api/types";

export interface TrafficHistoryPoint {
  windowSequence: number;
  windowEnd: string;
  windowStart: string | null;
  requestCount: number;
  allowedRequests: number;
  rejectedRequests: number;
  clientErrorRate: number;
  requestRate: number;
  ewmaRequestRate: number;
  errorRate: number;
}

export interface DecisionHistoryPoint extends DecisionStatus {
  key: string;
  windowSequence: number;
  proposedLimit: number;
}

export interface AnomalyHistoryPoint {
  key: string;
  timestamp: string;
  available: boolean;
  isAnomaly: boolean | null;
  anomalyScore: number | null;
  prediction: string | null;
  model: string | null;
  windowSequence: number | null;
}

export interface LimitHistoryPoint {
  key: string;
  windowSequence: number;
  timestamp: string;
  previousLimit: number;
  limit: number;
  action: DecisionAction | null;
}

export interface DashboardHistoryPoints {
  traffic: TrafficHistoryPoint[];
  decisions: DecisionHistoryPoint[];
  anomalies: AnomalyHistoryPoint[];
  limits: LimitHistoryPoint[];
}

function toDecisionAction(action: string | null): DecisionAction | null {
  if (
    action === "INCREASE_LIMIT" ||
    action === "DECREASE_LIMIT" ||
    action === "KEEP_LIMIT"
  ) {
    return action;
  }
  return null;
}

function mapTraffic(record: DashboardHistoryTraffic): TrafficHistoryPoint[] {
  if (record.windowSequence <= 0 || !record.windowEnd) {
    return [];
  }
  return [
    {
      windowSequence: record.windowSequence,
      windowEnd: record.windowEnd,
      windowStart: record.windowStart,
      requestCount: record.requestCount,
      allowedRequests: record.allowedRequests,
      rejectedRequests: record.rejectedRequests,
      clientErrorRate: record.clientErrorRate,
      requestRate: record.requestRate,
      ewmaRequestRate: record.ewmaRequestRate,
      errorRate: record.errorRate,
    },
  ];
}

function mapAnomaly(record: DashboardHistoryAnomaly): AnomalyHistoryPoint[] {
  if (!record.evaluatedAt) {
    return [];
  }
  return [
    {
      key: `anomaly-${record.windowSequence}-${record.evaluatedAt}`,
      timestamp: record.evaluatedAt,
      available: record.available,
      isAnomaly: record.isAnomaly,
      anomalyScore: record.anomalyScore,
      prediction: record.prediction,
      model: record.model,
      windowSequence: record.windowSequence,
    },
  ];
}

function mapDecision(record: DashboardHistoryDecision): DecisionHistoryPoint[] {
  const action = toDecisionAction(record.action);
  return [
    {
      key: `decision-${record.windowSequence}-${record.evaluatedAt ?? "none"}`,
      windowSequence: record.windowSequence,
      action,
      previousLimit: record.previousLimit,
      proposedLimit: record.proposedLimit,
      appliedLimit: record.appliedLimit,
      reasonCode: record.reasonCode,
      explanation: record.explanation,
      evaluatedAt: record.evaluatedAt,
    },
  ];
}

function mapLimit(record: DashboardHistoryLimit): LimitHistoryPoint[] {
  const action = toDecisionAction(record.action);
  return [
    {
      key: `limit-${record.windowSequence}-${record.evaluatedAt ?? "none"}`,
      windowSequence: record.windowSequence,
      timestamp: record.evaluatedAt ?? "",
      previousLimit: record.previousLimit,
      limit: record.limit,
      action,
    },
  ];
}

export function mapDashboardHistory(
  history: DashboardHistory | undefined,
): DashboardHistoryPoints {
  if (!history) {
    return { traffic: [], decisions: [], anomalies: [], limits: [] };
  }
  return {
    traffic: history.traffic.flatMap(mapTraffic),
    anomalies: history.anomalies.flatMap(mapAnomaly),
    decisions: history.decisions.flatMap(mapDecision),
    limits: history.limits.flatMap(mapLimit),
  };
}
