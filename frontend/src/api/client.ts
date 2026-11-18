import type {
  AdaptiveStatus,
  DashboardHistory,
  DemoResponse,
  HealthStatus,
  MlHealth,
  SystemStatus,
  TrafficMetrics,
} from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/+$/, "");
const ML_HEALTH_URL = import.meta.env.VITE_ML_HEALTH_URL || "/ml/health";
const REQUEST_TIMEOUT_MS = 8_000;

export class ApiError extends Error {
  readonly status: number | null;
  readonly url: string;

  constructor(message: string, url: string, status: number | null = null) {
    super(message);
    this.name = "ApiError";
    this.url = url;
    this.status = status;
  }
}

export function apiErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status !== null) {
      return `Request failed with HTTP ${error.status}.`;
    }
    if (error.name === "AbortError") {
      return "The request timed out before the backend responded.";
    }
    return "The backend could not be reached.";
  }
  if (error instanceof Error && error.name === "AbortError") {
    return "The request timed out before the backend responded.";
  }
  return "An unexpected error occurred while contacting the backend.";
}

async function requestJson<T>(
  url: string,
  signal?: AbortSignal,
  timeoutMs = REQUEST_TIMEOUT_MS,
): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);

  const forwardAbort = () => controller.abort();
  signal?.addEventListener("abort", forwardAbort, { once: true });

  try {
    const response = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json" },
      cache: "no-store",
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new ApiError(
        `Request failed with HTTP ${response.status}`,
        url,
        response.status,
      );
    }

    try {
      return (await response.json()) as T;
    } catch {
      throw new ApiError("The backend returned an invalid JSON response.", url);
    }
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiError("Request timed out.", url);
    }
    throw new ApiError("Network request failed.", url);
  } finally {
    window.clearTimeout(timeout);
    signal?.removeEventListener("abort", forwardAbort);
  }
}

function readHeader(response: Response, name: string): string | null {
  const value = response.headers.get(name);
  return value && value.trim() ? value : null;
}

async function requestDemo(
  clientId: string,
  signal?: AbortSignal,
  timeoutMs = REQUEST_TIMEOUT_MS,
): Promise<DemoResponse> {
  const url = `${API_BASE}/api/demo`;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);

  const forwardAbort = () => controller.abort();
  signal?.addEventListener("abort", forwardAbort, { once: true });

  const startedAt = performance.now();

  try {
    const response = await fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json",
        "X-Client-Id": clientId,
      },
      cache: "no-store",
      signal: controller.signal,
    });
    const body = await response.text();

    return {
      status: response.status,
      statusText: response.statusText,
      ok: response.ok,
      body,
      contentType: readHeader(response, "content-type"),
      latencyMs: performance.now() - startedAt,
      rateLimitLimit: readHeader(response, "x-ratelimit-limit"),
      rateLimitRemaining: readHeader(response, "x-ratelimit-remaining"),
      retryAfter: readHeader(response, "retry-after"),
    };
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiError("Request timed out.", url);
    }
    throw new ApiError("Network request failed.", url);
  } finally {
    window.clearTimeout(timeout);
    signal?.removeEventListener("abort", forwardAbort);
  }
}

export const api = {
  health: (signal?: AbortSignal) =>
    requestJson<HealthStatus>(`${API_BASE}/api/health`, signal),
  metrics: (signal?: AbortSignal) =>
    requestJson<TrafficMetrics>(`${API_BASE}/api/metrics`, signal),
  adaptiveStatus: (signal?: AbortSignal) =>
    requestJson<AdaptiveStatus>(`${API_BASE}/api/adaptive/status`, signal),
  systemStatus: (signal?: AbortSignal) =>
    requestJson<SystemStatus>(`${API_BASE}/api/system-status`, signal),
  history: (signal?: AbortSignal) =>
    requestJson<DashboardHistory>(`${API_BASE}/api/history`, signal),
  mlHealth: (signal?: AbortSignal) =>
    requestJson<MlHealth>(ML_HEALTH_URL, signal, 5_000),
  demoRequest: (clientId: string, signal?: AbortSignal) =>
    requestDemo(clientId, signal),
};
