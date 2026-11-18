import { CloudOff, PauseCircle } from "lucide-react";

import { useNow } from "../../hooks/useNow";
import { POLLING } from "../../hooks/useDashboardData";
import { formatDateTime, formatRelativeTime } from "../../lib/format";

interface StaleDataBannerProps {
  dataUpdatedAt: number;
  isError: boolean;
}

export function StaleDataBanner({
  dataUpdatedAt,
  isError,
}: StaleDataBannerProps) {
  const now = useNow();
  const age = dataUpdatedAt ? now - dataUpdatedAt : null;
  const isStale = age === null || age > POLLING.staleAfterMs;

  if (!isError && !isStale) {
    return null;
  }

  const Icon = isError ? CloudOff : PauseCircle;

  return (
    <div
      role="status"
      className="flex gap-3 rounded-xl border border-amber-200 bg-amber-50/80 px-4 py-3 text-xs leading-5 text-amber-900"
    >
      <Icon aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
      <div>
        <p className="font-semibold">
          {isError ? "Live refresh failed — showing stale data" : "Data may be paused"}
        </p>
        <p className="mt-0.5">
          {isError
            ? `The last successful update was ${formatRelativeTime(dataUpdatedAt || null)}`
            : `No fresh poll has completed in the last ${Math.round(POLLING.staleAfterMs / 1000)} seconds. Polling pauses while this tab is hidden.`}
          {dataUpdatedAt
            ? ` (${formatDateTime(new Date(dataUpdatedAt).toISOString())}).`
            : "."}
        </p>
      </div>
    </div>
  );
}
