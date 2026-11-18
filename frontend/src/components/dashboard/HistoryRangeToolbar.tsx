import type { ReactNode } from "react";
import { Clock3 } from "lucide-react";

import {
  TIME_RANGE_OPTIONS,
  type TimeRangeKey,
} from "../../lib/timeRange";

interface HistoryRangeToolbarProps {
  range: TimeRangeKey;
  onRangeChange: (range: TimeRangeKey) => void;
  rangeLabel: string;
  visibleCount: number;
  totalCount: number;
  itemLabel: string;
  children?: ReactNode;
}

export function HistoryRangeToolbar({
  range,
  onRangeChange,
  rangeLabel,
  visibleCount,
  totalCount,
  itemLabel,
  children,
}: HistoryRangeToolbarProps) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
          <Clock3 aria-hidden="true" className="h-3.5 w-3.5" />
          Time range
        </div>
        <div
          role="group"
          aria-label="History time range"
          className="flex flex-wrap gap-1"
        >
          {TIME_RANGE_OPTIONS.map((option) => (
            <button
              key={option.key}
              type="button"
              aria-pressed={range === option.key}
              onClick={() => onRangeChange(option.key)}
              className={`rounded-lg border px-2.5 py-1.5 text-xs font-semibold transition-colors focus-ring ${
                range === option.key
                  ? "border-brand-200 bg-brand-50 text-brand-700"
                  : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 sm:justify-end">
        <p
          aria-live="polite"
          className="text-[11px] leading-5 text-slate-500 sm:text-right"
        >
          <span className="font-semibold text-slate-700">{rangeLabel}</span>
          <br />
          Showing {visibleCount} of {totalCount} {itemLabel}
        </p>
        {children}
      </div>
    </div>
  );
}
