import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { DashboardData } from "../hooks/useDashboardData";
import {
  healthFixture,
  historyFixture,
  metricsFixture,
  statusFixture,
  systemStatusFixture,
} from "../test/fixtures";

vi.mock("../hooks/useDashboardData", () => ({
  POLLING: {
    metricsMs: 5_000,
    statusMs: 5_000,
    systemStatusMs: 10_000,
    historyMs: 10_000,
    healthMs: 10_000,
    staleAfterMs: 15_000,
  },
  useDashboardData: vi.fn(),
}));

import { useDashboardData } from "../hooks/useDashboardData";
import { SystemStatusPage } from "./SystemStatusPage";

const mockedUseDashboardData = vi.mocked(useDashboardData);

function queryResult<T>(
  data: T | undefined,
  overrides: Record<string, unknown> = {},
) {
  return {
    data,
    error: null,
    isError: false,
    isPending: false,
    isSuccess: data !== undefined,
    isFetching: false,
    dataUpdatedAt: Date.now(),
    refetch: vi.fn(),
    ...overrides,
  };
}

function dashboardData(
  overrides: Record<string, unknown> = {},
): DashboardData {
  return {
    metricsQuery: queryResult(metricsFixture()),
    statusQuery: queryResult(statusFixture()),
    systemStatusQuery: queryResult(systemStatusFixture()),
    historyQuery: queryResult(historyFixture()),
    healthQuery: queryResult(healthFixture()),
    trafficHistory: [],
    decisionHistory: [],
    anomalyHistory: [],
    limitHistory: [],
    ...overrides,
  } as unknown as DashboardData;
}

describe("SystemStatusPage", () => {
  beforeEach(() => {
    mockedUseDashboardData.mockReset();
  });

  it("shows verified store state and reports ML unavailability honestly", () => {
    const systemStatus = systemStatusFixture();
    systemStatus.ml = {
      enabled: true,
      checkedAt: "2026-01-01T10:00:32Z",
      available: false,
      modelReady: false,
      status: "unavailable",
      model: null,
      version: null,
      latencyMs: 50,
      lastSuccessfulEvaluationAt: null,
      lastFailureAt: "2026-01-01T10:00:31Z",
      lastFailureDetail: "ML anomaly assessment failed (ConnectException).",
      detail: "ML health check failed (ConnectException).",
      modelInfo: null,
    };

    mockedUseDashboardData.mockReturnValue(
      dashboardData({
        systemStatusQuery: queryResult(systemStatus),
      }),
    );

    render(<SystemStatusPage />);

    expect(screen.getByText("Counter store")).toBeInTheDocument();
    expect(screen.getByText("Memory")).toBeInTheDocument();
    expect(screen.getByText(/In-process counter store/i)).toBeInTheDocument();
    expect(screen.getAllByText("Unavailable").length).toBeGreaterThan(0);
    expect(screen.getByText(/ML health check failed/i)).toBeInTheDocument();
    expect(screen.getByText(/ML anomaly assessment failed/i)).toBeInTheDocument();
    expect(screen.getByText("Scheduled")).toBeInTheDocument();
    expect(screen.getByText("Last completed window")).toBeInTheDocument();
    expect(screen.getByText("Model metadata unavailable")).toBeInTheDocument();
  });

  it("shows a degraded store and refreshes backend snapshots on demand", () => {
    const systemStatus = systemStatusFixture();
    systemStatus.store = {
      type: "REDIS",
      available: false,
      verified: true,
      status: "degraded",
      detail: "Redis PING failed or timed out.",
      fallbackMode: "OPEN",
    };
    systemStatus.rateLimiter = {
      ...systemStatus.rateLimiter,
      store: "REDIS",
    };

    const data = dashboardData({
      systemStatusQuery: queryResult(systemStatus),
    });
    mockedUseDashboardData.mockReturnValue(data);

    render(<SystemStatusPage />);

    expect(screen.getByText("Adaptive mode")).toBeInTheDocument();
    expect(screen.getByText("Spring Boot backend")).toBeInTheDocument();
    expect(screen.getAllByText("Redis").length).toBeGreaterThan(0);
    expect(screen.getByText("Counter store is degraded")).toBeInTheDocument();
    expect(screen.getByText(/Fallback mode:/)).toBeInTheDocument();
    expect(screen.getAllByText("Open").length).toBeGreaterThan(0);
    expect(screen.getByText(/model ready:/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Check now" }));
    expect(data.systemStatusQuery.refetch).toHaveBeenCalledOnce();
    expect(data.healthQuery.refetch).toHaveBeenCalledOnce();
    expect(data.statusQuery.refetch).toHaveBeenCalledOnce();
    expect(data.metricsQuery.refetch).toHaveBeenCalledOnce();
  });
});
