import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { DashboardData } from "../hooks/useDashboardData";
import {
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
import { AnomaliesPage } from "./AnomaliesPage";

const mockedUseDashboardData = vi.mocked(useDashboardData);

function queryResult<T>(data: T | undefined, isError = false) {
  return {
    data,
    error: isError ? new Error("test error") : null,
    isError,
    isPending: data === undefined && !isError,
    isSuccess: data !== undefined,
    isFetching: false,
    dataUpdatedAt: Date.now(),
    refetch: vi.fn(),
  };
}

describe("AnomaliesPage", () => {
  it("shows an explicit inactive state in static mode", () => {
    const staticStatus = statusFixture({
      enabled: false,
      mode: "STATIC",
      currentLimit: 5,
      lastEvaluationAt: null,
      lastAdaptationAt: null,
      latestAnomaly: null,
      lastDecision: null,
    });

    mockedUseDashboardData.mockReturnValue({
      metricsQuery: queryResult(metricsFixture()),
      statusQuery: queryResult(staticStatus),
      systemStatusQuery: queryResult(systemStatusFixture()),
      historyQuery: queryResult(historyFixture()),
      healthQuery: queryResult(undefined),
      trafficHistory: [],
      decisionHistory: [],
      anomalyHistory: [],
      limitHistory: [],
    } as unknown as DashboardData);

    render(<AnomaliesPage />);

    expect(
      screen.getByText("No assessments in static mode"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/No anomaly values are fabricated/i),
    ).toBeInTheDocument();
  });

  it("renders an empty state when adaptive mode has no assessments yet", () => {
    const adaptiveStatus = statusFixture({
      latestAnomaly: null,
      lastEvaluationAt: null,
      lastDecision: null,
    });

    mockedUseDashboardData.mockReturnValue({
      metricsQuery: queryResult(metricsFixture()),
      statusQuery: queryResult(adaptiveStatus),
      systemStatusQuery: queryResult(systemStatusFixture()),
      historyQuery: queryResult(historyFixture()),
      healthQuery: queryResult(undefined),
      trafficHistory: [],
      decisionHistory: [],
      anomalyHistory: [],
      limitHistory: [],
    } as unknown as DashboardData);

    render(<AnomaliesPage />);

    expect(screen.getByText("No assessment yet")).toBeInTheDocument();
    expect(
      screen.getByText("No assessments observed yet"),
    ).toBeInTheDocument();
    expect(screen.getByText("Feature vector sent to ML")).toBeInTheDocument();
    expect(screen.getByText("request_rate")).toBeInTheDocument();
    expect(screen.getByText("Model metadata")).toBeInTheDocument();
    expect(screen.getByText(/2,?000/)).toBeInTheDocument();
  });
});
