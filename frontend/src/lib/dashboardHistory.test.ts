import { describe, expect, it } from "vitest";

import {
  mapDashboardHistory,
  type DashboardHistoryPoints,
} from "./dashboardHistory";
import { historyFixture } from "../test/fixtures";

const EMPTY: DashboardHistoryPoints = {
  traffic: [],
  decisions: [],
  anomalies: [],
  limits: [],
};

describe("dashboard history mapping", () => {
  it("returns empty series when history has not loaded", () => {
    expect(mapDashboardHistory(undefined)).toEqual(EMPTY);
  });

  it("maps backend records into chart-ready points", () => {
    const mapped = mapDashboardHistory(historyFixture());

    expect(mapped.traffic).toHaveLength(1);
    expect(mapped.traffic[0]?.windowSequence).toBe(4);
    expect(mapped.traffic[0]?.windowEnd).toBe("2026-01-01T10:00:30Z");
    expect(mapped.anomalies[0]?.timestamp).toBe("2026-01-01T10:00:31Z");
    expect(mapped.decisions[0]).toMatchObject({
      action: "INCREASE_LIMIT",
      previousLimit: 30,
      appliedLimit: 40,
      reasonCode: "LOW_LOAD",
    });
    expect(mapped.limits[0]).toMatchObject({
      limit: 40,
      action: "INCREASE_LIMIT",
    });
  });

  it("drops traffic records without a completed window", () => {
    const history = historyFixture({
      traffic: [
        {
          windowSequence: 0,
          windowStart: null,
          windowEnd: null,
          requestCount: 0,
          allowedRequests: 0,
          rejectedRequests: 0,
          clientErrorRate: 0,
          requestRate: 0,
          ewmaRequestRate: 0,
          errorRate: 0,
        },
      ],
    });

    expect(mapDashboardHistory(history).traffic).toEqual([]);
  });

  it("preserves unknown decision actions as null", () => {
    const history = historyFixture();
    history.decisions[0] = {
      ...history.decisions[0]!,
      action: "UNKNOWN_ACTION",
    };
    history.limits[0] = {
      ...history.limits[0]!,
      action: null,
    };

    const mapped = mapDashboardHistory(history);

    expect(mapped.decisions[0]?.action).toBeNull();
    expect(mapped.limits[0]?.action).toBeNull();
  });

  it("drops anomaly records without an evaluation timestamp", () => {
    const history = historyFixture();
    history.anomalies[0] = {
      ...history.anomalies[0]!,
      evaluatedAt: null,
    };

    expect(mapDashboardHistory(history).anomalies).toEqual([]);
  });
});
