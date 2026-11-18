import {
  Activity,
  AlertTriangle,
  ArrowUpRight,
  Clock3,
  Gauge,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import { apiErrorMessage } from "../api/client";
import { AllowedRejectedChart } from "../components/charts/AllowedRejectedChart";
import { ErrorRateChart } from "../components/charts/ErrorRateChart";
import { RejectionRateChart } from "../components/charts/RejectionRateChart";
import { TrafficTrendChart } from "../components/charts/TrafficTrendChart";
import { CsvExportButton } from "../components/dashboard/CsvExportButton";
import { HistoryNotice } from "../components/dashboard/HistoryNotice";
import { HistoryRangeToolbar } from "../components/dashboard/HistoryRangeToolbar";
import { StaleDataBanner } from "../components/dashboard/StaleDataBanner";
import { EmptyState } from "../components/ui/EmptyState";
import { ErrorState } from "../components/ui/ErrorState";
import { LoadingState } from "../components/ui/LoadingState";
import { MetricCard } from "../components/ui/MetricCard";
import { Notice } from "../components/ui/Notice";
import { PageHeader } from "../components/ui/PageHeader";
import { SectionCard } from "../components/ui/SectionCard";
import { useDashboardData } from "../hooks/useDashboardData";
import { useFilteredHistory } from "../hooks/useHistoryRange";
import { trafficCsv } from "../lib/csv";
import {
  formatDateTime,
  formatDecimal,
  formatInteger,
  formatPercent,
  formatRate,
} from "../lib/format";

export function TrafficPage() {
  const { metricsQuery, historyQuery, historyData } = useDashboardData();
  const range = useFilteredHistory(historyData);
  const metrics = metricsQuery.data;
  const trafficHistory = range.points.traffic;
  const rawTraffic = range.filteredHistory?.traffic ?? [];
  const recentWindows = [...trafficHistory].reverse();
  const totalTraffic = historyData?.traffic.length ?? 0;
  const rangeHasNoData =
    range.isFiltered && totalTraffic > 0 && trafficHistory.length === 0;
  const emptyTitle = rangeHasNoData
    ? "No traffic windows in the selected range"
    : undefined;
  const emptyDescription = rangeHasNoData
    ? "Widen the time range or wait for the backend to publish another completed window."
    : undefined;

  if (metricsQuery.isPending) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Operations"
          title="Traffic Monitoring"
          description="Completed monitoring windows, request rates, limiter outcomes, and error ratios reported by the backend."
        />
        <LoadingState />
      </div>
    );
  }

  if (metricsQuery.isError && !metrics) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Operations"
          title="Traffic Monitoring"
          description="Completed monitoring windows, request rates, limiter outcomes, and error ratios reported by the backend."
        />
        <ErrorState
          title="Traffic metrics unavailable"
          message={apiErrorMessage(metricsQuery.error)}
          onRetry={() => void metricsQuery.refetch()}
          isRetrying={metricsQuery.isFetching}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Operations"
        title="Traffic Monitoring"
        description="Completed monitoring windows, request rates, limiter outcomes, and error ratios reported by the backend."
        actions={<Notice tone="info">Values are for this backend instance only.</Notice>}
      />

      <StaleDataBanner
        dataUpdatedAt={metricsQuery.dataUpdatedAt}
        isError={metricsQuery.isError}
      />

      <HistoryRangeToolbar
        range={range.range}
        onRangeChange={range.setRange}
        rangeLabel={range.label}
        visibleCount={trafficHistory.length}
        totalCount={totalTraffic}
        itemLabel="traffic windows"
      >
        <CsvExportButton
          filename="traffic-history.csv"
          csv={trafficCsv(rawTraffic)}
          label="Export traffic CSV"
          disabled={rawTraffic.length === 0}
        />
      </HistoryRangeToolbar>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <MetricCard
          label="Current request rate"
          value={formatRate(metrics?.currentRequestRate)}
          helper="Last completed monitoring window"
          icon={Activity}
          tone="brand"
        />
        <MetricCard
          label="EWMA request rate"
          value={formatRate(metrics?.ewmaRequestRate)}
          helper="Exponentially weighted moving average"
          icon={Gauge}
          tone="violet"
        />
        <MetricCard
          label="Window requests"
          value={formatInteger(metrics?.windowRequestCount)}
          helper={`Window sequence ${formatInteger(metrics?.windowSequence)}`}
          icon={Clock3}
          tone="slate"
        />
        <MetricCard
          label="Allowed"
          value={formatInteger(metrics?.allowedRequests)}
          helper="Cumulative limiter approvals"
          icon={ShieldCheck}
          tone="emerald"
        />
        <MetricCard
          label="Rate-limited (429)"
          value={formatInteger(metrics?.rejectedRequests)}
          helper="Cumulative rejected by the limiter"
          icon={ShieldX}
          tone="rose"
        />
        <MetricCard
          label="Cumulative error rate"
          value={formatPercent(metrics?.errorRate)}
          helper="Includes rate-limit 429 responses"
          icon={AlertTriangle}
          tone="amber"
        />
      </div>

      <Notice tone="warning" title="How these counters are defined">
        <p>
          "Allowed" means approved by the limiter, not an HTTP 2xx response.
          "Rate-limited" is specifically HTTP 429. The backend does not expose an
          exact non-429 error count, so this dashboard does not estimate one.
        </p>
      </Notice>

      <div className="grid gap-4 xl:grid-cols-2">
        <SectionCard
          title="Request rate and EWMA trend"
          description="Current request rate compared with the backend-calculated EWMA across completed windows."
          actions={<span className="text-[11px] text-slate-400">Server history</span>}
        >
          <TrafficTrendChart
            data={trafficHistory}
            emptyTitle={emptyTitle}
            emptyDescription={emptyDescription}
          />
        </SectionCard>

        <SectionCard
          title="Error-rate trends"
          description="Per-window client error rate alongside the cumulative error rate reported at each window end."
          actions={<span className="text-[11px] text-slate-400">Server history</span>}
        >
          <ErrorRateChart
            data={trafficHistory}
            emptyTitle={emptyTitle}
            emptyDescription={emptyDescription}
          />
          <p className="mt-1 text-[11px] leading-5 text-slate-400">
            The dashed line is a cumulative snapshot at window end, not a
            per-window error rate. Client error rate excludes 429 responses.
          </p>
        </SectionCard>

        <SectionCard
          title="Allowed vs rejected requests"
          description="Limiter outcomes for each completed window in the selected range."
          actions={<span className="text-[11px] text-slate-400">Server history</span>}
        >
          <AllowedRejectedChart
            data={trafficHistory}
            emptyTitle={emptyTitle}
            emptyDescription={emptyDescription}
          />
        </SectionCard>

        <SectionCard
          title="Request rejection rate"
          description="429 responses divided by all limiter outcomes recorded for the window."
          actions={<span className="text-[11px] text-slate-400">Server history</span>}
        >
          <RejectionRateChart
            data={trafficHistory}
            emptyTitle={emptyTitle}
            emptyDescription={emptyDescription}
          />
        </SectionCard>
      </div>

      <SectionCard
        title="Observed monitoring windows"
        description="Every retained window in the selected range, newest first. Timestamps are backend-reported."
        noBodyPadding
      >
        {recentWindows.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState
              icon={<ArrowUpRight aria-hidden="true" className="h-5 w-5" />}
              title={emptyTitle ?? "No completed windows observed yet"}
              description={
                emptyDescription ??
                "A new row appears after the monitoring scheduler completes a window and the dashboard polls the updated sequence."
              }
            />
          </div>
        ) : (
          <div className="table-shell max-h-[32rem] overflow-y-auto">
            <table className="data-table">
              <caption className="sr-only">
                Traffic windows in the selected time range
              </caption>
              <thead className="sticky top-0 z-10">
                <tr>
                  <th scope="col">Window end</th>
                  <th scope="col">Requests</th>
                  <th scope="col">Allowed</th>
                  <th scope="col">429s</th>
                  <th scope="col">Client error</th>
                  <th scope="col">Cumulative error</th>
                  <th scope="col">Rate</th>
                  <th scope="col">EWMA</th>
                </tr>
              </thead>
              <tbody>
                {recentWindows.map((window) => (
                  <tr key={`${window.windowSequence}-${window.windowEnd}`}>
                    <td>
                      <div className="font-medium text-slate-800">
                        {formatDateTime(window.windowEnd)}
                      </div>
                      <div className="text-[11px] text-slate-400">
                        Sequence {formatInteger(window.windowSequence)}
                      </div>
                    </td>
                    <td>{formatInteger(window.requestCount)}</td>
                    <td className="text-emerald-700">
                      {formatInteger(window.allowedRequests)}
                    </td>
                    <td className="text-rose-700">
                      {formatInteger(window.rejectedRequests)}
                    </td>
                    <td>{formatPercent(window.clientErrorRate)}</td>
                    <td>{formatPercent(window.errorRate)}</td>
                    <td>{formatRate(window.requestRate)}</td>
                    <td>{formatDecimal(window.ewmaRequestRate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      <HistoryNotice historyQuery={historyQuery} />
    </div>
  );
}
