import { useMemo, useState } from "react";

import type { DashboardHistory } from "../api/types";
import {
  mapDashboardHistory,
  type DashboardHistoryPoints,
} from "../lib/dashboardHistory";
import {
  cutoffForRange,
  describeTimeRange,
  filterHistoryByRange,
  findTimeRangeOption,
  latestHistoryTimestamp,
  type TimeRangeKey,
  type TimeRangeOption,
} from "../lib/timeRange";

export interface HistoryRangeState {
  range: TimeRangeKey;
  setRange: (range: TimeRangeKey) => void;
  option: TimeRangeOption;
  anchor: string | null;
  filteredHistory: DashboardHistory | undefined;
  label: string;
  isFiltered: boolean;
  points: DashboardHistoryPoints;
}

export function useFilteredHistory(
  history: DashboardHistory | undefined,
): HistoryRangeState {
  const state = useHistoryRange(history);
  const points = useMemo(
    () => mapDashboardHistory(state.filteredHistory),
    [state.filteredHistory],
  );

  return { ...state, points };
}

export function useHistoryRange(
  history: DashboardHistory | undefined,
): HistoryRangeState {
  const [range, setRange] = useState<TimeRangeKey>("all");
  const anchor = useMemo(() => latestHistoryTimestamp(history), [history]);
  const cutoffMillis = useMemo(
    () => cutoffForRange(range, anchor),
    [range, anchor],
  );
  const filteredHistory = useMemo(
    () => filterHistoryByRange(history, cutoffMillis),
    [history, cutoffMillis],
  );
  const points = useMemo(
    () => mapDashboardHistory(filteredHistory),
    [filteredHistory],
  );
  const option = findTimeRangeOption(range);

  return {
    range,
    setRange,
    option,
    anchor,
    filteredHistory,
    label: describeTimeRange(option, anchor),
    isFiltered: cutoffMillis !== null,
    points,
  };
}
