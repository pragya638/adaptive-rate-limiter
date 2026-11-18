import { Activity, BrainCircuit, ShieldAlert } from "lucide-react";

import { apiErrorMessage } from "../api/client";
import { AnomalyHistoryChart } from "../components/charts/AnomalyHistoryChart";
import { AnomalySummaryCard } from "../components/dashboard/AnomalySummaryCard";
import { CsvExportButton } from "../components/dashboard/CsvExportButton";
import { HistoryNotice } from "../components/dashboard/HistoryNotice";
import { HistoryRangeToolbar } from "../components/dashboard/HistoryRangeToolbar";
import { MlServiceStatusCard } from "../components/dashboard/MlServiceStatusCard";
import { StaleDataBanner } from "../components/dashboard/StaleDataBanner";
import { Badge } from "../components/ui/Badge";
import { EmptyState } from "../components/ui/EmptyState";
import { ErrorState } from "../components/ui/ErrorState";
import { LoadingState } from "../components/ui/LoadingState";
import { ModeBadge } from "../components/ui/ModeBadge";
import { Notice } from "../components/ui/Notice";
import { PageHeader } from "../components/ui/PageHeader";
import { SectionCard } from "../components/ui/SectionCard";
import { useDashboardData } from "../hooks/useDashboardData";
import { useFilteredHistory } from "../hooks/useHistoryRange";
import { anomalyCsv } from "../lib/csv";
import {
  formatDateTime,
  formatDecimal,
  formatInteger,
  titleCase,
} from "../lib/format";

export function AnomaliesPage() {
  const {
    statusQuery,
    systemStatusQuery,
    historyQuery,
    historyData,
  } = useDashboardData();
  const range = useFilteredHistory(historyData);
  const status = statusQuery.data;
  const systemStatus = systemStatusQuery.data;
  const featureVector = status?.latestFeatureVector;
  const modelInfo = systemStatus?.ml.modelInfo;
  const anomalyHistory = range.points.anomalies;
  const rawAnomalies = range.filteredHistory?.anomalies ?? [];
  const totalAnomalies = historyData?.anomalies.length ?? 0;
  const rangeHasNoData =
    range.isFiltered && totalAnomalies > 0 && anomalyHistory.length === 0;
  const emptyTitle = rangeHasNoData
    ? "No anomaly assessments in the selected range"
    : undefined;
  const emptyDescription = rangeHasNoData
    ? "Widen the time range or wait for the backend to publish another adaptive evaluation."
    : undefined;

  if (statusQuery.isPending && !status) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Intelligence"
          title="Anomaly Detection"
          description="Inspect the latest Isolation Forest assessment and the assessments retained by the backend."
        />
        <LoadingState />
      </div>
    );
  }

  if (statusQuery.isError && !status) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Intelligence"
          title="Anomaly Detection"
          description="Inspect the latest Isolation Forest assessment and the assessments retained by the backend."
        />
        <ErrorState
          title="Adaptive status unavailable"
          message={apiErrorMessage(statusQuery.error)}
          onRetry={() => void statusQuery.refetch()}
          isRetrying={statusQuery.isFetching}
        />
      </div>
    );
  }

  const recentAssessments = [...anomalyHistory].reverse();

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Intelligence"
        title="Anomaly Detection"
        description="Inspect the latest Isolation Forest assessment, exact backend features, and assessments retained by the server. Scores are anomaly signals, not calibrated probabilities."
        actions={<ModeBadge mode={status?.mode} />}
      />

      <StaleDataBanner
        dataUpdatedAt={statusQuery.dataUpdatedAt}
        isError={statusQuery.isError}
      />

      <HistoryRangeToolbar
        range={range.range}
        onRangeChange={range.setRange}
        rangeLabel={range.label}
        visibleCount={anomalyHistory.length}
        totalCount={totalAnomalies}
        itemLabel="assessments"
      >
        <CsvExportButton
          filename="anomaly-history.csv"
          csv={anomalyCsv(rawAnomalies)}
          label="Export anomalies CSV"
          disabled={rawAnomalies.length === 0}
        />
      </HistoryRangeToolbar>

      {!status?.enabled ? (
        <>
          <Notice tone="info" title="Adaptive mode is inactive">
            The backend is running in static mode, so the ML assessment path is
            not being invoked. No anomaly values are fabricated for this view.
          </Notice>
          <SectionCard title="Latest anomaly assessment">
            <EmptyState
              icon={<ShieldAlert aria-hidden="true" className="h-5 w-5" />}
              title="No assessments in static mode"
              description="Anomaly detection runs as part of adaptive evaluation. It is currently disabled by the backend configuration."
            />
          </SectionCard>
        </>
      ) : (
        <>
          <div className="grid gap-4 xl:grid-cols-[1fr_1.35fr]">
            <AnomalySummaryCard status={status} />
            <SectionCard
              title="Anomaly score trend"
              description="Scores from assessments retained by the backend. Red bars are flagged anomalies."
              actions={<Badge tone="purple">Server history</Badge>}
            >
              <AnomalyHistoryChart
                data={anomalyHistory}
                emptyTitle={emptyTitle}
                emptyDescription={emptyDescription}
              />
            </SectionCard>
          </div>

          <MlServiceStatusCard systemStatus={systemStatus} />

          <div className="grid gap-4 xl:grid-cols-2">
            <SectionCard
              title="Feature vector sent to ML"
              description="Exact values produced by the backend mapper for the latest evaluation. Historical feature vectors are not retained."
            >
              {featureVector ? (
                <div>
                  <dl className="grid gap-4 text-sm sm:grid-cols-2">
                    <div>
                      <dt className="text-xs font-medium text-slate-500">
                        request_rate
                      </dt>
                      <dd className="mt-1 font-mono font-semibold text-slate-900">
                        {formatDecimal(featureVector.requestRate)}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs font-medium text-slate-500">
                        error_rate
                      </dt>
                      <dd className="mt-1 font-mono font-semibold text-slate-900">
                        {formatDecimal(featureVector.errorRate)}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs font-medium text-slate-500">
                        burst_ratio
                      </dt>
                      <dd className="mt-1 font-mono font-semibold text-slate-900">
                        {formatDecimal(featureVector.burstRatio)}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs font-medium text-slate-500">
                        request_count
                      </dt>
                      <dd className="mt-1 font-mono font-semibold text-slate-900">
                        {formatInteger(featureVector.requestCount)}
                      </dd>
                    </div>
                  </dl>
                  <p className="mt-4 border-t border-slate-100 pt-3 text-[11px] leading-5 text-slate-400">
                    Observed with the evaluation at {formatDateTime(status.lastEvaluationAt)}.
                    These values are read from the backend; they are not
                    recalculated in the browser.
                  </p>
                </div>
              ) : (
                <EmptyState
                  icon={<Activity aria-hidden="true" className="h-5 w-5" />}
                  title="No feature vector yet"
                  description="The backend has not completed an adaptive evaluation with ML features."
                />
              )}
            </SectionCard>

            <SectionCard
              title="Model metadata"
              description="Non-sensitive metadata reported by the ML service through the backend."
            >
              {modelInfo ? (
                <dl className="grid gap-4 text-sm sm:grid-cols-2">
                  <div>
                    <dt className="text-xs font-medium text-slate-500">Model</dt>
                    <dd className="mt-1 font-semibold text-slate-900">
                      {modelInfo.model ?? "-"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-slate-500">
                      Contamination
                    </dt>
                    <dd className="mt-1 font-mono font-semibold text-slate-900">
                      {formatDecimal(modelInfo.contamination)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-slate-500">
                      Estimators
                    </dt>
                    <dd className="mt-1 font-semibold text-slate-900">
                      {formatInteger(modelInfo.nEstimators)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-slate-500">
                      Training samples
                    </dt>
                    <dd className="mt-1 font-semibold text-slate-900">
                      {formatInteger(modelInfo.trainingSamples)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-slate-500">
                      Observation interval
                    </dt>
                    <dd className="mt-1 font-semibold text-slate-900">
                      {formatDecimal(modelInfo.observationIntervalSeconds)}s
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-slate-500">
                      Random state
                    </dt>
                    <dd className="mt-1 font-semibold text-slate-900">
                      {formatInteger(modelInfo.randomState)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-slate-500">
                      Fitted
                    </dt>
                    <dd className="mt-1 font-semibold text-slate-900">
                      {modelInfo.fitted ? "Yes" : "No"}
                    </dd>
                  </div>
                  <div className="sm:col-span-2">
                    <dt className="text-xs font-medium text-slate-500">
                      Feature names
                    </dt>
                    <dd className="mt-1 font-mono text-xs text-slate-700">
                      {modelInfo.featureNames?.join(", ") ?? "-"}
                    </dd>
                  </div>
                </dl>
              ) : (
                <EmptyState
                  icon={<BrainCircuit aria-hidden="true" className="h-5 w-5" />}
                  title="Model metadata unavailable"
                  description="The backend status snapshot did not include ML model metadata. No values are estimated here."
                />
              )}
            </SectionCard>
          </div>

          <Notice tone="info" title="Assessment availability">
            If the ML service cannot be reached, the backend may mark an
            assessment as unavailable and continue with its configured fallback.
            This dashboard displays that unavailable state rather than assuming a
            normal result.
          </Notice>

          <SectionCard
            title="Observed assessments"
            description="Every assessment retained by the backend in the selected range, newest first."
            noBodyPadding
          >
            {recentAssessments.length === 0 ? (
              <div className="p-5 sm:p-6">
                <EmptyState
                  icon={<Activity aria-hidden="true" className="h-5 w-5" />}
                  title={emptyTitle ?? "No assessments observed yet"}
                  description={
                    emptyDescription ??
                    "Wait for the backend to complete an adaptive evaluation. The retained history endpoint will include it after the next refresh."
                  }
                />
              </div>
            ) : (
              <div className="table-shell max-h-[32rem] overflow-y-auto">
                <table className="data-table">
                  <caption className="sr-only">
                    Anomaly assessments in the selected time range
                  </caption>
                  <thead className="sticky top-0 z-10">
                    <tr>
                      <th scope="col">Evaluated</th>
                      <th scope="col">Window</th>
                      <th scope="col">Result</th>
                      <th scope="col">Score</th>
                      <th scope="col">Prediction</th>
                      <th scope="col">Model</th>
                      <th scope="col">Availability</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentAssessments.map((assessment) => (
                      <tr key={assessment.key}>
                        <td>{formatDateTime(assessment.timestamp)}</td>
                        <td>
                          {assessment.windowSequence === null
                            ? "-"
                            : formatInteger(assessment.windowSequence)}
                        </td>
                        <td>
                          {!assessment.available ? (
                            <Badge tone="warning" dot>
                              Unavailable
                            </Badge>
                          ) : assessment.isAnomaly ? (
                            <Badge tone="danger" dot>
                              Anomaly
                            </Badge>
                          ) : (
                            <Badge tone="success" dot>
                              Normal
                            </Badge>
                          )}
                        </td>
                        <td className="font-mono">
                          {formatDecimal(assessment.anomalyScore)}
                        </td>
                        <td>{titleCase(assessment.prediction)}</td>
                        <td>{assessment.model ?? "-"}</td>
                        <td>
                          <span className="text-xs text-slate-500">
                            {assessment.available
                              ? "ML result available"
                              : "No ML result"}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </SectionCard>
        </>
      )}

      <HistoryNotice historyQuery={historyQuery} />
    </div>
  );
}
