import {
  createContext,
  useContext,
  useMemo,
  type ReactNode,
} from "react";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";

import { api } from "../api/client";
import type {
  AdaptiveStatus,
  DashboardHistory,
  HealthStatus,
  SystemStatus,
  TrafficMetrics,
} from "../api/types";
import {
  mapDashboardHistory,
  type AnomalyHistoryPoint,
  type DecisionHistoryPoint,
  type LimitHistoryPoint,
  type TrafficHistoryPoint,
} from "../lib/dashboardHistory";

export const POLLING = {
  metricsMs: 5_000,
  statusMs: 5_000,
  systemStatusMs: 10_000,
  historyMs: 10_000,
  healthMs: 10_000,
  staleAfterMs: 15_000,
} as const;

export interface DashboardData {
  metricsQuery: UseQueryResult<TrafficMetrics, Error>;
  statusQuery: UseQueryResult<AdaptiveStatus, Error>;
  systemStatusQuery: UseQueryResult<SystemStatus, Error>;
  historyQuery: UseQueryResult<DashboardHistory, Error>;
  healthQuery: UseQueryResult<HealthStatus, Error>;
  historyData: DashboardHistory | undefined;
  trafficHistory: TrafficHistoryPoint[];
  decisionHistory: DecisionHistoryPoint[];
  anomalyHistory: AnomalyHistoryPoint[];
  limitHistory: LimitHistoryPoint[];
}

const DashboardDataContext = createContext<DashboardData | null>(null);

export function DashboardDataProvider({ children }: { children: ReactNode }) {
  const metricsQuery = useQuery({
    queryKey: ["traffic-metrics"],
    queryFn: ({ signal }) => api.metrics(signal),
    refetchInterval: POLLING.metricsMs,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: POLLING.metricsMs - 1_000,
    retry: 1,
  });

  const statusQuery = useQuery({
    queryKey: ["adaptive-status"],
    queryFn: ({ signal }) => api.adaptiveStatus(signal),
    refetchInterval: POLLING.statusMs,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: POLLING.statusMs - 1_000,
    retry: 1,
  });

  const systemStatusQuery = useQuery({
    queryKey: ["system-status"],
    queryFn: ({ signal }) => api.systemStatus(signal),
    refetchInterval: POLLING.systemStatusMs,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: POLLING.systemStatusMs - 1_000,
    retry: 1,
  });

  const healthQuery = useQuery({
    queryKey: ["backend-health"],
    queryFn: ({ signal }) => api.health(signal),
    refetchInterval: POLLING.healthMs,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: POLLING.healthMs - 1_000,
    retry: 1,
  });

  const historyQuery = useQuery({
    queryKey: ["dashboard-history"],
    queryFn: ({ signal }) => api.history(signal),
    refetchInterval: POLLING.historyMs,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: POLLING.historyMs - 1_000,
    retry: 1,
  });

  const history = useMemo(
    () => mapDashboardHistory(historyQuery.data),
    [historyQuery.data],
  );

  const value = useMemo<DashboardData>(
    () => ({
      metricsQuery,
      statusQuery,
      systemStatusQuery,
      historyQuery,
      healthQuery,
      historyData: historyQuery.data,
      trafficHistory: history.traffic,
      decisionHistory: history.decisions,
      anomalyHistory: history.anomalies,
      limitHistory: history.limits,
    }),
    [
      metricsQuery,
      statusQuery,
      systemStatusQuery,
      historyQuery,
      healthQuery,
      history,
      historyQuery.data,
    ],
  );

  return (
    <DashboardDataContext.Provider value={value}>
      {children}
    </DashboardDataContext.Provider>
  );
}

export function useDashboardData(): DashboardData {
  const value = useContext(DashboardDataContext);
  if (!value) {
    throw new Error(
      "useDashboardData must be used inside DashboardDataProvider.",
    );
  }
  return value;
}
