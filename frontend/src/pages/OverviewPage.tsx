import {
  Activity,
  CircleGauge,
  ShieldCheck,
  ShieldX,
  TrendingUp,
} from "lucide-react";

import { apiErrorMessage } from "../api/client";
import { RequestOutcomeChart } from "../components/charts/RequestOutcomeChart";
import { TrafficTrendChart } from "../components/charts/TrafficTrendChart";
import { AnomalySummaryCard } from "../components/dashboard/AnomalySummaryCard";
import { DecisionSummaryCard } from "../components/dashboard/DecisionSummaryCard";
import { HistoryNotice } from "../components/dashboard/HistoryNotice";
import { StaleDataBanner } from "../components/dashboard/StaleDataBanner";
import { EmptyState } from "../components/ui/EmptyState";
import { ErrorState } from "../components/ui/ErrorState";
import { LoadingState } from "../components/ui/LoadingState";
import { MetricCard } from "../components/ui/MetricCard";
import { Notice } from "../components/ui/Notice";
import { PageHeader } from "../components/ui/PageHeader";
import { SectionCard } from "../components/ui/SectionCard";
import { useDashboardData } from "../hooks/useDashboardData";
import {
  formatInteger,
  formatPercent,
  formatRate,
} from "../lib/format";

export function OverviewPage() {
  const { metricsQuery, statusQuery, historyQuery, trafficHistory } =
    useDashboardData();
  const metrics = metricsQuery.data;
  const status = statusQuery.data;
  const isInitialLoading =
    metricsQuery.isPending && statusQuery.isPending;
  const isFullyOffline =
    metricsQuery.isError && statusQuery.isError && !metrics && !status;

  if (isInitialLoading) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Operations"
          title="Overview"
          description="A consolidated, read-only snapshot of the rate limiter, traffic monitoring, and adaptive controller."
        />
        <LoadingState />
      </div>
    );
  }

  if (isFullyOffline) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Operations"
          title="Overview"
          description="A consolidated, read-only snapshot of the rate limiter, traffic monitoring, and adaptive controller."
        />
        <ErrorState
          title="Backend offline"
          message={apiErrorMessage(metricsQuery.error ?? statusQuery.error)}
          onRetry={() => {
            void metricsQuery.refetch();
            void statusQuery.refetch();
          }}
          isRetrying={metricsQuery.isFetching || statusQuery.isFetching}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Operations"
        title="Overview"
        description="A consolidated, read-only snapshot of the rate limiter, traffic monitoring, and adaptive controller. Values are reported by the running backend."
        actions={
          <Notice tone="info" className="hidden max-w-xs xl:flex">
            Counters are instance-local and reset when the backend restarts.
          </Notice>
        }
      />

      <StaleDataBanner
        dataUpdatedAt={metricsQuery.dataUpdatedAt}
        isError={metricsQuery.isError || statusQuery.isError}
      />

      {metricsQuery.isError && !metrics ? (
        <ErrorState
          title="Metrics unavailable"
          message={apiErrorMessage(metricsQuery.error)}
          onRetry={() => void metricsQuery.refetch()}
          isRetrying={metricsQuery.isFetching}
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              label="Active request limit"
              value={`${formatInteger(status?.currentLimit)} / window`}
              helper={
                status
                  ? `${status.mode === "ADAPTIVE" ? "Adaptive" : "Static"} configuration reported by the backend`
                  : "Adaptive status is unavailable"
              }
              icon={CircleGauge}
              tone="brand"
              unavailable={!status}
            />
            <MetricCard
              label="Current request rate"
              value={formatRate(metrics?.currentRequestRate)}
              helper="Rate for the last completed monitoring window"
              icon={Activity}
              tone="violet"
              unavailable={!metrics}
            />
            <MetricCard
              label="Allowed requests"
              value={formatInteger(metrics?.allowedRequests)}
              helper="Cumulative limiter approvals; not a count of HTTP 2xx responses"
              icon={ShieldCheck}
              tone="emerald"
              unavailable={!metrics}
            />
            <MetricCard
              label="Rate-limited requests"
              value={formatInteger(metrics?.rejectedRequests)}
              helper="Cumulative HTTP 429 responses emitted by the limiter"
              icon={ShieldX}
              tone="rose"
              unavailable={!metrics}
            />
          </div>

          <div className="grid gap-4 xl:grid-cols-[1.6fr_1fr]">
            <SectionCard
              title="Traffic rate"
              description="Current and EWMA request rates from completed monitoring windows."
              actions={
                <span className="text-[11px] font-medium text-slate-400">
                  Server history
                </span>
              }
            >
              <TrafficTrendChart data={trafficHistory} />
              {trafficHistory.length === 1 ? (
                <p className="mt-1 text-center text-[11px] text-slate-400">
                  One window observed. The trend appears after the next completed
                  window.
                </p>
              ) : null}
            </SectionCard>

            <SectionCard
              title="Request outcomes"
              description="Cumulative limiter outcomes for this backend instance."
            >
              <RequestOutcomeChart metrics={metrics} />
              <p className="text-center text-[11px] leading-5 text-slate-400">
                Exact HTTP error counts are not exposed by the backend. Only
                rate-limit rejections are counted separately.
              </p>
            </SectionCard>
          </div>

          <div className="grid gap-4 xl:grid-cols-2">
            {statusQuery.isError && !status ? (
              <SectionCard title="Adaptive status unavailable">
                <EmptyState
                  compact
                  title="Status endpoint unavailable"
                  description={apiErrorMessage(statusQuery.error)}
                />
              </SectionCard>
            ) : (
              <AnomalySummaryCard status={status} />
            )}
            {statusQuery.isError && !status ? (
              <SectionCard title="Adaptive decisions unavailable">
                <EmptyState
                  compact
                  title="Status endpoint unavailable"
                  description={apiErrorMessage(statusQuery.error)}
                />
              </SectionCard>
            ) : (
              <DecisionSummaryCard status={status} />
            )}
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="card p-4">
              <p className="text-xs font-medium text-slate-500">
                Window requests
              </p>
              <p className="mt-1 text-lg font-semibold text-slate-900">
                {formatInteger(metrics?.windowRequestCount)}
              </p>
              <p className="mt-0.5 text-[11px] text-slate-400">
                Last completed window
              </p>
            </div>
            <div className="card p-4">
              <p className="text-xs font-medium text-slate-500">
                Cumulative error rate
              </p>
              <p className="mt-1 text-lg font-semibold text-slate-900">
                {formatPercent(metrics?.errorRate)}
              </p>
              <p className="mt-0.5 text-[11px] text-slate-400">
                Includes rate-limit 429 responses
              </p>
            </div>
            <div className="card p-4">
              <p className="text-xs font-medium text-slate-500">
                Window client-error rate
              </p>
              <p className="mt-1 text-lg font-semibold text-slate-900">
                {formatPercent(metrics?.clientErrorRate)}
              </p>
              <p className="mt-0.5 text-[11px] text-slate-400">
                Excludes 429 responses
              </p>
            </div>
          </div>

          <HistoryNotice historyQuery={historyQuery} />
        </>
      )}

      <div className="flex items-center gap-2 text-[11px] text-slate-400">
        <TrendingUp aria-hidden="true" className="h-3.5 w-3.5" />
        Live values refresh every 5 seconds while this tab is visible.
      </div>
    </div>
  );
}
