import { Clock3, RefreshCw } from "lucide-react";

import { useNow } from "../../hooks/useNow";
import { POLLING } from "../../hooks/useDashboardData";
import { cn } from "../../lib/cn";
import { formatDateTime, formatRelativeTime } from "../../lib/format";

interface LastUpdatedProps {
  dataUpdatedAt: number;
  isFetching: boolean;
  isError: boolean;
}

export function LastUpdated({
  dataUpdatedAt,
  isFetching,
  isError,
}: LastUpdatedProps) {
  const now = useNow();
  const age = dataUpdatedAt ? now - dataUpdatedAt : null;
  const isStale = age === null || age > POLLING.staleAfterMs;

  return (
    <div
      className={cn(
        "inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-medium",
        isError || isStale
          ? "border-amber-200 bg-amber-50 text-amber-800"
          : "border-slate-200 bg-white text-slate-600",
      )}
      title={dataUpdatedAt ? formatDateTime(new Date(dataUpdatedAt).toISOString()) : undefined}
    >
      {isFetching ? (
        <RefreshCw aria-hidden="true" className="h-3.5 w-3.5 animate-spin" />
      ) : (
        <Clock3 aria-hidden="true" className="h-3.5 w-3.5" />
      )}
      <span>
        {isError ? "Stale data - " : "Updated "}
        {formatRelativeTime(dataUpdatedAt || null)}
      </span>
    </div>
  );
}
