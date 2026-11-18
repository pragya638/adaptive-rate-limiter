import { useState } from "react";
import {
  ArrowRight,
  CircleGauge,
  Gauge,
  Info,
  Layers,
  ShieldCheck,
  ShieldX,
  SlidersHorizontal,
} from "lucide-react";

import type { AdaptiveMode } from "../api/types";
import { apiErrorMessage } from "../api/client";
import { LimitTimelineChart } from "../components/charts/LimitTimelineChart";
import { DecisionSummaryCard } from "../components/dashboard/DecisionSummaryCard";
import { HistoryNotice } from "../components/dashboard/HistoryNotice";
import { RequestTester } from "../components/rate-limiting/RequestTester";
import { StaleDataBanner } from "../components/dashboard/StaleDataBanner";
import { Badge } from "../components/ui/Badge";
import { EmptyState } from "../components/ui/EmptyState";
import { ErrorState } from "../components/ui/ErrorState";
import { LoadingState } from "../components/ui/LoadingState";
import { MetricCard } from "../components/ui/MetricCard";
import { ModeBadge } from "../components/ui/ModeBadge";
import { Notice } from "../components/ui/Notice";
import { PageHeader } from "../components/ui/PageHeader";
import { SectionCard } from "../components/ui/SectionCard";
import { useDashboardData } from "../hooks/useDashboardData";
import {
  formatDateTime,
  formatInteger,
  formatPercent,
} from "../lib/format";

function normalizeMode(mode: string | undefined): AdaptiveMode {
  return mode === "ADAPTIVE" ? "ADAPTIVE" : "STATIC";
}

export function RateLimitingPage() {
  const {
    metricsQuery,
    statusQuery,
    systemStatusQuery,
    historyQuery,
    limitHistory,
  } = useDashboardData();
  const [selectedView, setSelectedView] = useState<AdaptiveMode | null>(null);

  const status = statusQuery.data;
  const metrics = metricsQuery.data;
  const systemStatus = systemStatusQuery.data;
  const rateLimiter = systemStatus?.rateLimiter;
  const store = systemStatus?.store;

  if (statusQuery.isPending && !status) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Operations"
          title="Rate Limiting"
          description="Inspect the active limiter configuration and the adaptive controller without changing runtime behavior."
        />
        <LoadingState />
      </div>
    );
  }

  if (statusQuery.isError && !status) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Operations"
          title="Rate Limiting"
          description="Inspect the active limiter configuration and the adaptive controller without changing runtime behavior."
        />
        <ErrorState
          title="Rate-limiter status unavailable"
          message={apiErrorMessage(statusQuery.error)}
          onRetry={() => void statusQuery.refetch()}
          isRetrying={statusQuery.isFetching}
        />
      </div>
    );
  }

  const activeMode = normalizeMode(status?.mode);
  const view = selectedView ?? activeMode;
  const viewingInactiveMode = view !== activeMode;

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Operations"
        title="Rate Limiting"
        description="Inspect the active limiter configuration and the adaptive controller without changing runtime behavior. View tabs only change what this page displays."
        actions={
          <>
            <ModeBadge mode={status?.mode} />
            <Badge tone="neutral">Read-only</Badge>
          </>
        }
      />

      <StaleDataBanner
        dataUpdatedAt={statusQuery.dataUpdatedAt}
        isError={statusQuery.isError || metricsQuery.isError}
      />

      {store?.status === "degraded" ? (
        <Notice tone="warning" title="Counter store is degraded">
          {store.detail} Requests are following the backend's configured failure
          mode until the store recovers.
        </Notice>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Active request limit"
          value={formatInteger(status?.currentLimit)}
          helper="Requests per rate-limit window"
          icon={CircleGauge}
          tone="brand"
        />
        <MetricCard
          label="Backend mode"
          value={activeMode === "ADAPTIVE" ? "Adaptive" : "Static"}
          helper="Reported by /api/adaptive/status"
          icon={Layers}
          tone={activeMode === "ADAPTIVE" ? "violet" : "slate"}
        />
        <MetricCard
          label="Allowed requests"
          value={formatInteger(metrics?.allowedRequests)}
          helper="Cumulative limiter approvals"
          icon={ShieldCheck}
          tone="emerald"
          unavailable={!metrics}
        />
        <MetricCard
          label="Rate-limited requests"
          value={formatInteger(metrics?.rejectedRequests)}
          helper="Cumulative HTTP 429 responses"
          icon={ShieldX}
          tone="rose"
          unavailable={!metrics}
        />
      </div>

      <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-2 shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <div
          role="tablist"
          aria-label="Rate limiting views"
          className="flex gap-1"
        >
          <button
            type="button"
            role="tab"
            aria-selected={view === "STATIC"}
            onClick={() => setSelectedView("STATIC")}
            className={`tab-button ${
              view === "STATIC"
                ? "bg-brand-600 text-white shadow-sm"
                : "text-slate-600 hover:bg-slate-100"
            }`}
          >
            <Gauge aria-hidden="true" className="h-4 w-4" />
            Static view
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={view === "ADAPTIVE"}
            onClick={() => setSelectedView("ADAPTIVE")}
            className={`tab-button ${
              view === "ADAPTIVE"
                ? "bg-violet-600 text-white shadow-sm"
                : "text-slate-600 hover:bg-slate-100"
            }`}
          >
            <SlidersHorizontal aria-hidden="true" className="h-4 w-4" />
            Adaptive view
          </button>
        </div>
        <p className="px-2 text-[11px] text-slate-400">
          These tabs do not switch the backend mode.
        </p>
      </div>

      {viewingInactiveMode ? (
        <Notice tone="warning" title={`${view === "STATIC" ? "Static" : "Adaptive"} view is inactive`}>
          The backend is currently running in{" "}
          <span className="font-semibold">{activeMode.toLowerCase()}</span> mode.
          You are inspecting the other view for reference only; no runtime
          setting is changed.
        </Notice>
      ) : null}

      <RequestTester
        currentLimit={status?.currentLimit}
        onRunComplete={() => void metricsQuery.refetch()}
      />

      {view === "STATIC" ? (
        <div className="space-y-4">
          <div className="grid gap-4 xl:grid-cols-[1.2fr_1fr]">
            <SectionCard
              title="Static limiter configuration"
              description="Reported by the read-only system-status endpoint."
            >
              <div className="flex items-center gap-4 rounded-xl bg-brand-50 px-4 py-4 ring-1 ring-brand-100">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white text-brand-700 shadow-sm ring-1 ring-brand-100">
                  <Gauge aria-hidden="true" className="h-5 w-5" />
                </span>
                <div>
                  <p className="text-2xl font-bold text-brand-900">
                    {formatInteger(
                      rateLimiter?.configuredMaxRequests ??
                        (activeMode === "STATIC" ? status?.currentLimit : undefined),
                    )}
                  </p>
                  <p className="text-xs font-medium text-brand-700">
                    configured requests allowed per window
                  </p>
                </div>
              </div>
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-xs font-medium text-slate-500">
                    Runtime mode
                  </dt>
                  <dd className="mt-0.5 font-semibold text-slate-900">
                    {activeMode === "STATIC" ? "Static" : "Adaptive"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-slate-500">
                    Window length
                  </dt>
                  <dd className="mt-0.5 font-semibold text-slate-900">
                    {rateLimiter ? `${rateLimiter.windowSeconds}s` : "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-slate-500">
                    Counter store
                  </dt>
                  <dd className="mt-0.5 font-semibold text-slate-900">
                    {rateLimiter?.store ?? "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-slate-500">
                    Client-error rate
                  </dt>
                  <dd className="mt-0.5 font-semibold text-slate-900">
                    {formatPercent(metrics?.clientErrorRate)}
                  </dd>
                </div>
              </dl>
              {activeMode === "ADAPTIVE" ? (
                <p className="mt-4 text-xs leading-5 text-slate-500">
                  The adaptive controller is applying{" "}
                  {formatInteger(status?.currentLimit)} requests per window. The
                  static value above is the configured fallback that would apply
                  if adaptive mode were disabled.
                </p>
              ) : null}
            </SectionCard>

            <SectionCard
              title="Limit observations"
              description="Active limits retained by the backend across completed evaluations."
            >
              <LimitTimelineChart data={limitHistory} />
            </SectionCard>
          </div>

          <SectionCard
            title="Limit paths"
            description="Static include and exclude rules reported by the backend."
          >
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Rate-limited paths
                </dt>
                <dd className="mt-1 font-mono text-xs text-slate-700">
                  {rateLimiter?.includePaths?.join(", ") ?? "—"}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Excluded paths
                </dt>
                <dd className="mt-1 font-mono text-xs text-slate-700">
                  {rateLimiter?.excludePaths?.join(", ") ?? "—"}
                </dd>
              </div>
            </dl>
          </SectionCard>
        </div>
      ) : activeMode === "ADAPTIVE" ? (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              label="Current limit"
              value={formatInteger(status?.currentLimit)}
              helper="Applied by the adaptive controller"
              icon={CircleGauge}
              tone="violet"
            />
            <MetricCard
              label="Minimum limit"
              value={formatInteger(status?.minLimit)}
              helper="Configured lower bound"
              icon={Gauge}
              tone="slate"
            />
            <MetricCard
              label="Maximum limit"
              value={formatInteger(status?.maxLimit)}
              helper="Configured upper bound"
              icon={Gauge}
              tone="slate"
            />
            <MetricCard
              label="Initial limit"
              value={formatInteger(status?.initialLimit)}
              helper="Controller starting value"
              icon={Gauge}
              tone="slate"
            />
          </div>

          <div className="grid gap-4 xl:grid-cols-[1.35fr_1fr]">
            <SectionCard
              title="Adaptive limit timeline"
              description="Limit values retained by the backend across completed evaluations."
              actions={<Badge tone="purple">Server history</Badge>}
            >
              <LimitTimelineChart data={limitHistory} />
            </SectionCard>

            <SectionCard
              title="Controller timing"
              description="Timestamps from the most recent adaptive evaluations."
            >
              <dl className="space-y-4 text-sm">
                <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-3">
                  <dt className="text-slate-500">Last evaluation</dt>
                  <dd className="text-right font-medium text-slate-800">
                    {formatDateTime(status?.lastEvaluationAt)}
                  </dd>
                </div>
                <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-3">
                  <dt className="text-slate-500">Last adaptation</dt>
                  <dd className="text-right font-medium text-slate-800">
                    {formatDateTime(status?.lastAdaptationAt)}
                  </dd>
                </div>
                <div className="flex items-start justify-between gap-4">
                  <dt className="text-slate-500">Latest action</dt>
                  <dd className="text-right font-medium text-slate-800">
                    {status?.lastDecision?.action ?? "No decision reported"}
                  </dd>
                </div>
              </dl>
              <div className="mt-5 flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-[11px] text-slate-500 ring-1 ring-slate-200">
                <ArrowRight aria-hidden="true" className="h-3.5 w-3.5" />
                Bounds and timing are backend-reported values.
              </div>
            </SectionCard>
          </div>

          <DecisionSummaryCard status={status} />
        </div>
      ) : (
        <div className="space-y-4">
          <Notice tone="info" title="Adaptive evaluation is disabled">
            The backend is running in static mode. The bounds below are configured
            values from the status endpoint, but no anomaly assessment or adaptive
            decision is currently produced.
          </Notice>
          <div className="grid gap-4 sm:grid-cols-3">
            <MetricCard
              label="Minimum limit"
              value={formatInteger(status?.minLimit)}
              helper="Configured lower bound"
              icon={Gauge}
              tone="slate"
            />
            <MetricCard
              label="Maximum limit"
              value={formatInteger(status?.maxLimit)}
              helper="Configured upper bound"
              icon={Gauge}
              tone="slate"
            />
            <MetricCard
              label="Initial limit"
              value={formatInteger(status?.initialLimit)}
              helper="Controller starting value"
              icon={Gauge}
              tone="slate"
            />
          </div>
          <SectionCard
            title="No adaptive evaluation"
            description="Adaptive controller state is inactive in static mode."
          >
            <EmptyState
              icon={<Info aria-hidden="true" className="h-5 w-5" />}
              title="Adaptive decisions are not being generated"
              description="Enable adaptive mode in the backend configuration to produce anomaly assessments and limit decisions. This dashboard cannot change the runtime mode."
            />
          </SectionCard>
        </div>
      )}

      <HistoryNotice historyQuery={historyQuery} />
    </div>
  );
}
