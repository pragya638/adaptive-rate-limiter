import type { UseQueryResult } from "@tanstack/react-query";
import { History, RefreshCw } from "lucide-react";

import { apiErrorMessage } from "../../api/client";
import type { DashboardHistory } from "../../api/types";
import { Notice } from "../ui/Notice";

export function HistoryNotice({
  historyQuery,
}: {
  historyQuery: UseQueryResult<DashboardHistory, Error>;
}) {
  if (historyQuery.isError) {
    return (
      <Notice tone="danger" title="Server history unavailable">
        <p>
          {apiErrorMessage(historyQuery.error)} Charts and tables below may be
          empty because retained history could not be loaded.
        </p>
        <button
          type="button"
          onClick={() => void historyQuery.refetch()}
          disabled={historyQuery.isFetching}
          className="mt-2 inline-flex items-center gap-1.5 rounded-md border border-rose-300 bg-white px-2 py-1 font-semibold text-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <RefreshCw
            aria-hidden="true"
            className={`h-3.5 w-3.5 ${historyQuery.isFetching ? "animate-spin" : ""}`}
          />
          Retry history
        </button>
      </Notice>
    );
  }

  if (historyQuery.isPending || !historyQuery.data) {
    return (
      <Notice tone="info" title="Loading server history">
        The dashboard is requesting the backend's retained observations.
      </Notice>
    );
  }

  const history = historyQuery.data;
  const storage =
    history.storage === "MEMORY" ? "backend memory" : history.storage;
  const retention = history.survivesRestart
    ? "It survives backend restarts."
    : "It resets when the backend restarts.";

  return (
    <div className="flex gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs leading-5 text-slate-500">
      <History
        aria-hidden="true"
        className="mt-0.5 h-4 w-4 shrink-0 text-slate-400"
      />
      <p>
        <span className="font-semibold text-slate-700">Server history.</span>{" "}
        The backend retains the latest {history.maxRecords} observations per
        series in {storage}. It survives dashboard reloads while the backend
        runs. {retention}
      </p>
    </div>
  );
}
