import type { DashboardHistory } from "../api/types";

export const TIME_RANGE_OPTIONS = [
  { key: "all", label: "All retained", durationMs: null },
  { key: "5m", label: "Last 5 min", durationMs: 5 * 60 * 1000 },
  { key: "15m", label: "Last 15 min", durationMs: 15 * 60 * 1000 },
  { key: "1h", label: "Last 1 hour", durationMs: 60 * 60 * 1000 },
] as const;

export type TimeRangeKey = (typeof TIME_RANGE_OPTIONS)[number]["key"];
export type TimeRangeOption = (typeof TIME_RANGE_OPTIONS)[number];

export function findTimeRangeOption(key: TimeRangeKey): TimeRangeOption {
  return TIME_RANGE_OPTIONS.find((option) => option.key === key) ?? TIME_RANGE_OPTIONS[0];
}

export function latestHistoryTimestamp(
  history: DashboardHistory | undefined,
): string | null {
  if (!history) {
    return null;
  }

  const timestamps = [
    ...history.traffic.map((record) => record.windowEnd),
    ...history.anomalies.map((record) => record.evaluatedAt),
    ...history.decisions.map((record) => record.evaluatedAt),
    ...history.limits.map((record) => record.evaluatedAt),
  ].filter((value): value is string => Boolean(value));

  let latest: string | null = null;
  let latestMillis = Number.NEGATIVE_INFINITY;

  for (const timestamp of timestamps) {
    const millis = Date.parse(timestamp);
    if (!Number.isNaN(millis) && millis > latestMillis) {
      latest = timestamp;
      latestMillis = millis;
    }
  }

  return latest;
}

export function cutoffForRange(
  key: TimeRangeKey,
  anchor: string | null,
): number | null {
  const option = findTimeRangeOption(key);
  if (option.durationMs === null || !anchor) {
    return null;
  }

  const anchorMillis = Date.parse(anchor);
  if (Number.isNaN(anchorMillis)) {
    return null;
  }

  return anchorMillis - option.durationMs;
}

export function timestampInRange(
  timestamp: string | null,
  cutoffMillis: number | null,
): boolean {
  if (cutoffMillis === null) {
    return true;
  }
  if (!timestamp) {
    return false;
  }

  const millis = Date.parse(timestamp);
  return !Number.isNaN(millis) && millis >= cutoffMillis;
}

export function filterHistoryByRange(
  history: DashboardHistory | undefined,
  cutoffMillis: number | null,
): DashboardHistory | undefined {
  if (!history || cutoffMillis === null) {
    return history;
  }

  return {
    ...history,
    traffic: history.traffic.filter((record) =>
      timestampInRange(record.windowEnd, cutoffMillis),
    ),
    anomalies: history.anomalies.filter((record) =>
      timestampInRange(record.evaluatedAt, cutoffMillis),
    ),
    decisions: history.decisions.filter((record) =>
      timestampInRange(record.evaluatedAt, cutoffMillis),
    ),
    limits: history.limits.filter((record) =>
      timestampInRange(record.evaluatedAt, cutoffMillis),
    ),
  };
}

export function describeTimeRange(
  option: TimeRangeOption,
  anchor: string | null,
): string {
  if (!anchor) {
    return "No retained records";
  }

  const formattedAnchor = new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(anchor));

  if (option.key === "all") {
    return `All retained history - ending ${formattedAnchor}`;
  }

  return `${option.label} - ending ${formattedAnchor}`;
}
