const integerFormatter = new Intl.NumberFormat(undefined, {
  maximumFractionDigits: 0,
});

const decimalFormatter = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
  year: "numeric",
  month: "short",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

const shortTimeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

export const EMPTY_VALUE = "-";

export function formatInteger(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return EMPTY_VALUE;
  }
  return integerFormatter.format(value);
}

export function formatDecimal(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return EMPTY_VALUE;
  }
  return decimalFormatter.format(value);
}

export function formatRate(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return EMPTY_VALUE;
  }
  return `${decimalFormatter.format(value)} req/s`;
}

export function formatPercent(
  value: number | null | undefined,
  digits = 2,
): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return EMPTY_VALUE;
  }
  return `${(value * 100).toFixed(digits)}%`;
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || Number.isNaN(seconds)) {
    return EMPTY_VALUE;
  }
  return `${decimalFormatter.format(seconds)}s`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return EMPTY_VALUE;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return EMPTY_VALUE;
  }
  return dateTimeFormatter.format(date);
}

export function formatShortTime(value: string | null | undefined): string {
  if (!value) {
    return EMPTY_VALUE;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return EMPTY_VALUE;
  }
  return shortTimeFormatter.format(date);
}

export function formatRelativeTime(timestamp: number | null | undefined): string {
  if (!timestamp) {
    return "not yet";
  }
  const seconds = Math.max(0, Math.round((Date.now() - timestamp) / 1000));
  if (seconds < 5) {
    return "just now";
  }
  if (seconds < 60) {
    return `${seconds}s ago`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m ago`;
  }
  const hours = Math.floor(minutes / 60);
  return `${hours}h ago`;
}

export function formatMode(mode: string | null | undefined): string {
  if (mode === "ADAPTIVE") {
    return "Adaptive";
  }
  if (mode === "STATIC") {
    return "Static";
  }
  return mode ?? EMPTY_VALUE;
}

export function titleCase(value: string | null | undefined): string {
  if (!value) {
    return EMPTY_VALUE;
  }
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}
