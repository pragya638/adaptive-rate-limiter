import type {
  DashboardHistoryAnomaly,
  DashboardHistoryDecision,
} from "../api/types";

export type DecisionActionKey =
  | "INCREASE_LIMIT"
  | "DECREASE_LIMIT"
  | "KEEP_LIMIT"
  | "UNKNOWN";

export type AnomalyCategory =
  | "anomaly"
  | "normal"
  | "unavailable"
  | "not_reported";

export interface DecisionDistribution {
  INCREASE_LIMIT: number;
  DECREASE_LIMIT: number;
  KEEP_LIMIT: number;
  UNKNOWN: number;
  total: number;
}

export interface DecisionAnomalyRow {
  key: string;
  windowSequence: number;
  evaluatedAt: string | null;
  anomaly: DashboardHistoryAnomaly | null;
  decision: DashboardHistoryDecision;
}

export interface AnomalyDecisionCount {
  category: AnomalyCategory;
  total: number;
  INCREASE_LIMIT: number;
  DECREASE_LIMIT: number;
  KEEP_LIMIT: number;
  UNKNOWN: number;
}

export function normalizeDecisionAction(
  action: string | null,
): DecisionActionKey {
  if (
    action === "INCREASE_LIMIT" ||
    action === "DECREASE_LIMIT" ||
    action === "KEEP_LIMIT"
  ) {
    return action;
  }
  return "UNKNOWN";
}

export function countDecisionActions(
  decisions: DashboardHistoryDecision[],
): DecisionDistribution {
  const distribution: DecisionDistribution = {
    INCREASE_LIMIT: 0,
    DECREASE_LIMIT: 0,
    KEEP_LIMIT: 0,
    UNKNOWN: 0,
    total: decisions.length,
  };

  for (const decision of decisions) {
    distribution[normalizeDecisionAction(decision.action)] += 1;
  }

  return distribution;
}

export function anomalyCategory(
  anomaly: DashboardHistoryAnomaly | null | undefined,
): AnomalyCategory {
  if (!anomaly) {
    return "not_reported";
  }
  if (!anomaly.available) {
    return "unavailable";
  }
  return anomaly.isAnomaly === true ? "anomaly" : "normal";
}

export function joinDecisionAnomalies(
  decisions: DashboardHistoryDecision[],
  anomalies: DashboardHistoryAnomaly[],
): DecisionAnomalyRow[] {
  const anomaliesByWindow = new Map<number, DashboardHistoryAnomaly>();
  for (const anomaly of anomalies) {
    anomaliesByWindow.set(anomaly.windowSequence, anomaly);
  }

  return decisions.map((decision) => ({
    key: `correlation-${decision.windowSequence}-${decision.evaluatedAt ?? "none"}`,
    windowSequence: decision.windowSequence,
    evaluatedAt: decision.evaluatedAt,
    anomaly: anomaliesByWindow.get(decision.windowSequence) ?? null,
    decision,
  }));
}

export function countAnomalyDecisionPairs(
  rows: DecisionAnomalyRow[],
): AnomalyDecisionCount[] {
  const categories: AnomalyCategory[] = [
    "anomaly",
    "normal",
    "unavailable",
    "not_reported",
  ];
  const counts: AnomalyDecisionCount[] = categories.map((category) => ({
    category,
    total: 0,
    INCREASE_LIMIT: 0,
    DECREASE_LIMIT: 0,
    KEEP_LIMIT: 0,
    UNKNOWN: 0,
  }));
  const byCategory = new Map(counts.map((count) => [count.category, count]));

  for (const row of rows) {
    const count = byCategory.get(anomalyCategory(row.anomaly));
    if (!count) {
      continue;
    }
    count.total += 1;
    count[normalizeDecisionAction(row.decision.action)] += 1;
  }

  return counts.filter((count) => count.total > 0);
}
