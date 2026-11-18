import type {
  DashboardHistoryAnomaly,
  DashboardHistoryDecision,
  DashboardHistoryLimit,
  DashboardHistoryTraffic,
} from "../api/types";

import type { DecisionAnomalyRow } from "./decisionAnalytics";

export function escapeCsvCell(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }

  const text = String(value);
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

export function toCsv(
  headers: readonly string[],
  rows: readonly (readonly unknown[])[],
): string {
  return [headers, ...rows]
    .map((row) => row.map(escapeCsvCell).join(","))
    .join("\r\n");
}

function finiteOrEmpty(value: number | null | undefined): number | string {
  return value === null || value === undefined || Number.isNaN(value)
    ? ""
    : value;
}

export function trafficCsv(records: DashboardHistoryTraffic[]): string {
  return toCsv(
    [
      "window_sequence",
      "window_start",
      "window_end",
      "request_count",
      "allowed_requests",
      "rejected_requests",
      "request_rate",
      "ewma_request_rate",
      "client_error_rate",
      "error_rate",
    ],
    records.map((record) => [
      record.windowSequence,
      record.windowStart ?? "",
      record.windowEnd ?? "",
      record.requestCount,
      record.allowedRequests,
      record.rejectedRequests,
      record.requestRate,
      record.ewmaRequestRate,
      record.clientErrorRate,
      record.errorRate,
    ]),
  );
}

export function anomalyCsv(records: DashboardHistoryAnomaly[]): string {
  return toCsv(
    [
      "window_sequence",
      "evaluated_at",
      "available",
      "is_anomaly",
      "anomaly_score",
      "prediction",
      "model",
    ],
    records.map((record) => [
      record.windowSequence,
      record.evaluatedAt ?? "",
      record.available,
      record.isAnomaly ?? "",
      finiteOrEmpty(record.anomalyScore),
      record.prediction ?? "",
      record.model ?? "",
    ]),
  );
}

export function decisionCsv(records: DashboardHistoryDecision[]): string {
  return toCsv(
    [
      "window_sequence",
      "evaluated_at",
      "action",
      "previous_limit",
      "proposed_limit",
      "applied_limit",
      "reason_code",
      "explanation",
    ],
    records.map((record) => [
      record.windowSequence,
      record.evaluatedAt ?? "",
      record.action ?? "",
      record.previousLimit,
      record.proposedLimit,
      record.appliedLimit,
      record.reasonCode ?? "",
      record.explanation ?? "",
    ]),
  );
}

export function limitCsv(records: DashboardHistoryLimit[]): string {
  return toCsv(
    [
      "window_sequence",
      "evaluated_at",
      "action",
      "previous_limit",
      "limit",
    ],
    records.map((record) => [
      record.windowSequence,
      record.evaluatedAt ?? "",
      record.action ?? "",
      record.previousLimit,
      record.limit,
    ]),
  );
}

export function decisionAnomalyCorrelationCsv(
  rows: DecisionAnomalyRow[],
): string {
  return toCsv(
    [
      "window_sequence",
      "evaluated_at",
      "anomaly_available",
      "is_anomaly",
      "anomaly_score",
      "prediction",
      "decision_action",
      "previous_limit",
      "proposed_limit",
      "applied_limit",
      "reason_code",
      "explanation",
    ],
    rows.map((row) => [
      row.windowSequence,
      row.evaluatedAt,
      row.anomaly?.available ?? "",
      row.anomaly?.isAnomaly ?? "",
      finiteOrEmpty(row.anomaly?.anomalyScore ?? null),
      row.anomaly?.prediction ?? "",
      row.decision.action ?? "",
      row.decision.previousLimit,
      row.decision.proposedLimit,
      row.decision.appliedLimit,
      row.decision.reasonCode ?? "",
      row.decision.explanation ?? "",
    ]),
  );
}

export function downloadCsv(filename: string, contents: string): void {
  const blob = new Blob([`\uFEFF${contents}`], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = "none";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
