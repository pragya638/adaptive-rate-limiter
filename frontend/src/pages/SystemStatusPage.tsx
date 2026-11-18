import {
  BrainCircuit,
  Database,
  Gauge,
  RefreshCw,
  Server,
  Waypoints,
  type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";

import { apiErrorMessage } from "../api/client";
import { HistoryNotice } from "../components/dashboard/HistoryNotice";
import { StaleDataBanner } from "../components/dashboard/StaleDataBanner";
import { Badge, type BadgeTone } from "../components/ui/Badge";
import { EmptyState } from "../components/ui/EmptyState";
import { ModeBadge } from "../components/ui/ModeBadge";
import { Notice } from "../components/ui/Notice";
import { PageHeader } from "../components/ui/PageHeader";
import { SectionCard } from "../components/ui/SectionCard";
import { POLLING, useDashboardData } from "../hooks/useDashboardData";
import {
  formatDateTime,
  formatDecimal,
  formatInteger,
  formatRelativeTime,
  titleCase,
} from "../lib/format";

interface DependencyCardProps {
  title: string;
  icon: LucideIcon;
  state: string;
  tone: BadgeTone;
  children: ReactNode;
}

function DependencyCard({
  title,
  icon: Icon,
  state,
  tone,
  children,
}: DependencyCardProps) {
  return (
    <div className="card p-5">
      <div className="flex items-start justify-between gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-600 ring-1 ring-slate-200">
          <Icon aria-hidden="true" className="h-5 w-5" />
        </span>
        <Badge tone={tone} dot>
          {state}
        </Badge>
      </div>
      <h2 className="mt-3 text-sm font-semibold text-slate-900">{title}</h2>
      <div className="mt-1.5 text-xs leading-5 text-slate-500">{children}</div>
    </div>
  );
}

function storeTone(status: string | undefined): BadgeTone {
  if (status === "ready") {
    return "success";
  }
  if (status === "degraded") {
    return "warning";
  }
  return "danger";
}

function mlTone(available: boolean, modelReady: boolean, status: string): BadgeTone {
  if (status === "disabled") {
    return "neutral";
  }
  if (!available) {
    return "warning";
  }
  return modelReady ? "success" : "warning";
}

export function SystemStatusPage() {
  const {
    metricsQuery,
    statusQuery,
    systemStatusQuery,
    historyQuery,
    healthQuery,
  } = useDashboardData();

  const systemStatus = systemStatusQuery.data;
  const store = systemStatus?.store;
  const ml = systemStatus?.ml;
  const rateLimiter = systemStatus?.rateLimiter;
  const adaptive = systemStatus?.adaptive ?? statusQuery.data;
  const metrics = metricsQuery.data;

  const apiDataConnected =
    metricsQuery.isSuccess && statusQuery.isSuccess
      ? "connected"
      : metricsQuery.isSuccess || statusQuery.isSuccess
        ? "degraded"
        : metricsQuery.isError && statusQuery.isError
          ? "offline"
          : "checking";

  const backendOperational =
    systemStatus?.backend.status === "UP" || healthQuery.isSuccess;

  const refreshAll = () => {
    void systemStatusQuery.refetch();
    void healthQuery.refetch();
    void statusQuery.refetch();
    void metricsQuery.refetch();
  };

  const lastDataUpdate = Math.max(
    metricsQuery.dataUpdatedAt,
    statusQuery.dataUpdatedAt,
    systemStatusQuery.dataUpdatedAt,
    healthQuery.dataUpdatedAt,
  );

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Platform"
        title="System Status"
        description="Read-only connectivity and visibility for the backend, dashboard data sources, rate-limit store, monitoring loop, and ML service."
        actions={
          <>
            <ModeBadge mode={adaptive?.mode} />
            <button type="button" onClick={refreshAll} className="btn-secondary">
              <RefreshCw aria-hidden="true" className="h-4 w-4" />
              Check now
            </button>
          </>
        }
      />

      <StaleDataBanner
        dataUpdatedAt={lastDataUpdate}
        isError={
          healthQuery.isError ||
          metricsQuery.isError ||
          statusQuery.isError ||
          systemStatusQuery.isError
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <DependencyCard
          title="Spring Boot backend"
          icon={Server}
          state={backendOperational ? "Operational" : "Unavailable"}
          tone={backendOperational ? "success" : "danger"}
        >
          {backendOperational ? (
            <>
              <span className="font-medium text-slate-700">
                {systemStatus?.backend.service ?? "adaptive-rate-limiter"}
              </span>{" "}
              reports status {systemStatus?.backend.status ?? "UP"}. Snapshot
              observed {formatRelativeTime(systemStatusQuery.dataUpdatedAt || null)}.
            </>
          ) : (
            <>{apiErrorMessage(healthQuery.error ?? systemStatusQuery.error)}</>
          )}
        </DependencyCard>

        <DependencyCard
          title="Dashboard data APIs"
          icon={Waypoints}
          state={
            apiDataConnected === "connected"
              ? "Connected"
              : apiDataConnected === "degraded"
                ? "Degraded"
                : apiDataConnected === "offline"
                  ? "Offline"
                  : "Checking"
          }
          tone={
            apiDataConnected === "connected"
              ? "success"
              : apiDataConnected === "degraded"
                ? "warning"
                : apiDataConnected === "offline"
                  ? "danger"
                  : "neutral"
          }
        >
          {apiDataConnected === "degraded"
            ? "One of the metrics or adaptive-status endpoints is unavailable. Some views may be stale."
            : apiDataConnected === "connected"
              ? "Both /api/metrics and /api/adaptive/status are responding."
              : apiDataConnected === "offline"
                ? "Neither metrics nor adaptive status could be loaded."
                : "Checking metrics and adaptive status..."}
        </DependencyCard>

        <DependencyCard
          title="Counter store"
          icon={Database}
          state={
            systemStatusQuery.isError
              ? "Not verified"
              : store
                ? titleCase(store.status)
                : "Checking"
          }
          tone={
            systemStatusQuery.isError
              ? "warning"
              : store
                ? storeTone(store.status)
                : "neutral"
          }
        >
          {systemStatusQuery.isError ? (
            <>The backend status snapshot could not be loaded.</>
          ) : store ? (
            <>
              <span className="font-medium text-slate-700">
                {titleCase(store.type)}
              </span>{" "}
              store. {store.detail ?? "No detail reported."}
              {store.fallbackMode ? (
                <>
                  {" "}
                  Fallback mode:{" "}
                  <span className="font-medium text-slate-700">
                    {titleCase(store.fallbackMode)}
                  </span>
                  .
                </>
              ) : null}
            </>
          ) : (
            <>Checking the active counter store...</>
          )}
        </DependencyCard>

        <DependencyCard
          title="ML anomaly service"
          icon={BrainCircuit}
          state={
            !systemStatusQuery.isSuccess
              ? "Checking"
              : ml?.status === "disabled"
                ? "Disabled"
                : ml?.available
                  ? ml.modelReady
                    ? "Operational"
                    : "Degraded"
                  : "Unavailable"
          }
          tone={
            !systemStatusQuery.isSuccess || !ml
              ? "neutral"
              : mlTone(ml.available, ml.modelReady, ml.status)
          }
        >
          {!systemStatusQuery.isSuccess || !ml ? (
            <>Checking backend-reported ML status...</>
          ) : ml.status === "disabled" ? (
            <>{ml.detail}</>
          ) : ml.available ? (
            <>
              <span className="font-medium text-slate-700">{ml.model}</span>{" "}
              {ml.version ? `v${ml.version}` : ""}; model ready:{" "}
              {ml.modelReady ? "yes" : "no"}. Health latency:{" "}
              {formatInteger(ml.latencyMs)} ms.
              {ml.detail ? <> {ml.detail}</> : null}
            </>
          ) : (
            <>{ml.detail ?? "The backend could not reach the ML service."}</>
          )}
        </DependencyCard>
      </div>

      {store?.status === "degraded" ? (
        <Notice tone="warning" title="Counter store is degraded">
          {store.detail} Check Redis connectivity and the configured failure mode.
        </Notice>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-[1.25fr_1fr]">
        <SectionCard
          title="Backend-reported state"
          description="Values come from the consolidated read-only system-status snapshot."
        >
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs font-medium text-slate-500">Mode</dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {titleCase(rateLimiter?.mode ?? adaptive?.mode)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Active limit
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatInteger(rateLimiter?.activeLimit ?? adaptive?.currentLimit)}{" "}
                requests / window
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Configured static limit
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatInteger(rateLimiter?.configuredMaxRequests)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Rate-limit window
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {rateLimiter ? `${rateLimiter.windowSeconds}s` : "-"}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Monitoring window
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                Sequence{" "}
                {formatInteger(
                  systemStatus?.monitoring.latestWindow?.sequence ??
                    metrics?.windowSequence,
                )}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Monitoring scheduler
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {titleCase(systemStatus?.monitoring.schedulerStatus)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Last completed window
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatDateTime(systemStatus?.monitoring.lastCompletedAt)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Pending evaluations
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatInteger(systemStatus?.monitoring.pendingWindows)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Last evaluation
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatDateTime(adaptive?.lastEvaluationAt)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Last successful ML evaluation
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatDateTime(ml?.lastSuccessfulEvaluationAt)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Last ML failure
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatDateTime(ml?.lastFailureAt)}
              </dd>
            </div>
            {ml?.lastFailureDetail ? (
              <div className="sm:col-span-2">
                <dt className="text-xs font-medium text-slate-500">
                  Last ML failure detail
                </dt>
                <dd className="mt-1 text-slate-700">
                  {ml.lastFailureDetail}
                </dd>
              </div>
            ) : null}
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Store configuration
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {rateLimiter?.store ?? "-"}
              </dd>
            </div>
          </dl>
          {systemStatusQuery.isError ? (
            <Notice tone="danger" className="mt-5">
              The system-status endpoint is currently unreachable. Cached data
              remains visible, and polling retries automatically.
            </Notice>
          ) : null}
        </SectionCard>

        <SectionCard
          title="Dashboard data policy"
          description="How this client collects and refreshes data."
        >
          <ul className="space-y-3 text-sm">
            <li className="flex items-start justify-between gap-4 border-b border-slate-100 pb-3">
              <span className="text-slate-500">Metrics polling</span>
              <span className="text-right font-medium text-slate-800">
                Every {POLLING.metricsMs / 1000}s
              </span>
            </li>
            <li className="flex items-start justify-between gap-4 border-b border-slate-100 pb-3">
              <span className="text-slate-500">System status polling</span>
              <span className="text-right font-medium text-slate-800">
                Every {POLLING.systemStatusMs / 1000}s
              </span>
            </li>
            <li className="flex items-start justify-between gap-4 border-b border-slate-100 pb-3">
              <span className="text-slate-500">Paused while tab hidden</span>
              <span className="text-right font-medium text-slate-800">Yes</span>
            </li>
            <li className="flex items-start justify-between gap-4">
              <span className="text-slate-500">History storage</span>
              <span className="text-right font-medium text-slate-800">
                {historyQuery.data
                  ? `${historyQuery.data.storage === "MEMORY" ? "Backend memory" : historyQuery.data.storage}; ${historyQuery.data.survivesRestart ? "persists across backend restarts" : "reload-safe, resets on backend restart"}`
                  : "Backend memory (bounded); reload-safe, resets on restart"}
              </span>
            </li>
          </ul>
        </SectionCard>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <SectionCard
          title="ML model metadata"
          description="Reported by the ML service through the backend status check."
          actions={<Badge tone="purple">Model</Badge>}
        >
          {ml?.modelInfo ? (
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs font-medium text-slate-500">Model</dt>
                <dd className="mt-1 font-semibold text-slate-900">
                  {ml.modelInfo.model ?? "-"}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Contamination
                </dt>
                <dd className="mt-1 font-mono font-semibold text-slate-900">
                  {formatDecimal(ml.modelInfo.contamination)}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Estimators
                </dt>
                <dd className="mt-1 font-semibold text-slate-900">
                  {formatInteger(ml.modelInfo.nEstimators)}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Training samples
                </dt>
                <dd className="mt-1 font-semibold text-slate-900">
                  {formatInteger(ml.modelInfo.trainingSamples)}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Observation interval
                </dt>
                <dd className="mt-1 font-semibold text-slate-900">
                  {formatDecimal(ml.modelInfo.observationIntervalSeconds)}s
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Random state
                </dt>
                <dd className="mt-1 font-semibold text-slate-900">
                  {formatInteger(ml.modelInfo.randomState)}
                </dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-xs font-medium text-slate-500">
                  Feature names
                </dt>
                <dd className="mt-1 font-mono text-xs text-slate-700">
                  {ml.modelInfo.featureNames?.join(", ") ?? "-"}
                </dd>
              </div>
            </dl>
          ) : (
            <EmptyState
              icon={<Gauge aria-hidden="true" className="h-5 w-5" />}
              title="Model metadata unavailable"
              description="The ML service did not return model metadata. The backend does not estimate or cache model details."
            />
          )}
        </SectionCard>

        <SectionCard
          title="Rate-limit paths and monitoring"
          description="Static limiter configuration and monitoring loop settings."
        >
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs font-medium text-slate-500">
                Observation interval
              </dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatDecimal(systemStatus?.monitoring.observationIntervalSeconds)}s
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-slate-500">EWMA alpha</dt>
              <dd className="mt-1 font-semibold text-slate-900">
                {formatDecimal(systemStatus?.monitoring.ewmaAlpha)}
              </dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-xs font-medium text-slate-500">
                Rate-limited paths
              </dt>
              <dd className="mt-1 font-mono text-xs text-slate-700">
                {rateLimiter?.includePaths?.join(", ") ?? "-"}
              </dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-xs font-medium text-slate-500">
                Excluded paths
              </dt>
              <dd className="mt-1 font-mono text-xs text-slate-700">
                {rateLimiter?.excludePaths?.join(", ") ?? "-"}
              </dd>
            </div>
          </dl>
        </SectionCard>
      </div>

      <Notice tone="info" title="History storage">
        The backend retains bounded metrics, anomaly, and decision history in
        memory and exposes it via{" "}
        <span className="font-mono">/api/history</span>. This history survives
        dashboard reloads while the backend runs but resets when the backend
        application restarts. No persistent Redis-backed history storage is used.
      </Notice>

      <HistoryNotice historyQuery={historyQuery} />
    </div>
  );
}
