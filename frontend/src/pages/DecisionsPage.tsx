import { useMemo } from "react";
import {
  ArrowRight,
  CircleGauge,
  GitBranch,
  SlidersHorizontal,
} from "lucide-react";

import type { DecisionStatus } from "../api/types";
import { apiErrorMessage } from "../api/client";
import { DecisionDistributionChart } from "../components/charts/DecisionDistributionChart";
import { LimitTimelineChart } from "../components/charts/LimitTimelineChart";
import { DecisionSummaryCard } from "../components/dashboard/DecisionSummaryCard";
import { CsvExportButton } from "../components/dashboard/CsvExportButton";
import { HistoryNotice } from "../components/dashboard/HistoryNotice";
import { HistoryRangeToolbar } from "../components/dashboard/HistoryRangeToolbar";
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
import { useFilteredHistory } from "../hooks/useHistoryRange";
import {
  decisionAnomalyCorrelationCsv,
  decisionCsv,
} from "../lib/csv";
import { decisionActionTone } from "../lib/decision";
import {
  countAnomalyDecisionPairs,
  joinDecisionAnomalies,
  normalizeDecisionAction,
} from "../lib/decisionAnalytics";
import {
  formatDateTime,
  formatDecimal,
  formatInteger,
  titleCase,
} from "../lib/format";

function DecisionBadge({ decision }: { decision: DecisionStatus }) {
  return (
    <Badge tone={decisionActionTone(decision.action)} dot>
      {titleCase(decision.action)}
    </Badge>
  );
}

export function DecisionsPage() {
  const { statusQuery, historyQuery, historyData } = useDashboardData();
  const range = useFilteredHistory(historyData);
  const status = statusQuery.data;
  const decisionHistory = range.points.decisions;
  const limitHistory = range.points.limits;
  const rawDecisions = range.filteredHistory?.decisions ?? [];
  const rawAnomalies = range.filteredHistory?.anomalies ?? [];
  const correlationRows = useMemo(
    () => joinDecisionAnomalies(rawDecisions, rawAnomalies),
    [rawDecisions, rawAnomalies],
  );
  const correlationCounts = useMemo(
    () => countAnomalyDecisionPairs(correlationRows),
    [correlationRows],
  );
  const totalDecisions = historyData?.decisions.length ?? 0;
  const rangeHasNoData =
    range.isFiltered && totalDecisions > 0 && decisionHistory.length === 0;
  const emptyTitle = rangeHasNoData
    ? "No decisions in the selected range"
    : undefined;
  const emptyDescription = rangeHasNoData
    ? "Widen the time range or wait for the backend to publish another adaptive evaluation."
    : undefined;

  if (statusQuery.isPending && !status) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Intelligence"
          title="Adaptive Decisions"
          description="Inspect the latest adaptive controller decision and the decisions retained by the backend."
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
          title="Adaptive Decisions"
          description="Inspect the latest adaptive controller decision and the decisions retained by the backend."
        />
        <ErrorState
          title="Adaptive decision status unavailable"
          message={apiErrorMessage(statusQuery.error)}
          onRetry={() => void statusQuery.refetch()}
          isRetrying={statusQuery.isFetching}
        />
      </div>
    );
  }

  const recentDecisions = [...decisionHistory].reverse();

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Intelligence"
        title="Adaptive Decisions"
        description="Inspect limit changes, decision outcomes, and their descriptive relationship to backend anomaly assessments. No runtime policy controls are available here."
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
        visibleCount={decisionHistory.length}
        totalCount={totalDecisions}
        itemLabel="decisions"
      >
        <div className="flex flex-wrap gap-2">
          <CsvExportButton
            filename="decision-history.csv"
            csv={decisionCsv(rawDecisions)}
            label="Export decisions CSV"
            disabled={rawDecisions.length === 0}
          />
          <CsvExportButton
            filename="decision-anomaly-correlation.csv"
            csv={decisionAnomalyCorrelationCsv(correlationRows)}
            label="Export correlation CSV"
            disabled={correlationRows.length === 0}
          />
        </div>
      </HistoryRangeToolbar>

      {!status?.enabled ? (
        <>
          <Notice tone="info" title="Adaptive mode is inactive">
            The backend is running in static mode. No adaptive decisions are
            generated, so there is no decision history to display.
          </Notice>
          <SectionCard title="Latest adaptive decision">
            <EmptyState
              icon={<GitBranch aria-hidden="true" className="h-5 w-5" />}
              title="No decisions in static mode"
              description="Adaptive decisions require adaptive mode to be enabled by the backend configuration. This dashboard cannot change that setting."
            />
          </SectionCard>
        </>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              label="Current limit"
              value={formatInteger(status.currentLimit)}
              helper="Active requests per window"
              icon={CircleGauge}
              tone="violet"
            />
            <MetricCard
              label="Minimum bound"
              value={formatInteger(status.minLimit)}
              helper="Configured lower bound"
              icon={SlidersHorizontal}
              tone="slate"
            />
            <MetricCard
              label="Maximum bound"
              value={formatInteger(status.maxLimit)}
              helper="Configured upper bound"
              icon={SlidersHorizontal}
              tone="slate"
            />
            <MetricCard
              label="Last evaluation"
              value={
                status.lastEvaluationAt
                  ? formatDateTime(status.lastEvaluationAt)
                  : "Not yet"
              }
              helper="Latest completed controller evaluation"
              icon={GitBranch}
              tone="brand"
            />
          </div>

          <div className="grid gap-4 xl:grid-cols-[1.35fr_1fr]">
            <SectionCard
              title="Adaptive limit timeline"
              description="Limit values retained by the backend across completed evaluations."
              actions={<Badge tone="purple">Server history</Badge>}
            >
              <LimitTimelineChart
                data={limitHistory}
                emptyTitle={emptyTitle}
                emptyDescription={emptyDescription}
              />
            </SectionCard>
            <DecisionSummaryCard status={status} />
          </div>

          <div className="grid gap-4 xl:grid-cols-[1fr_1.2fr]">
            <SectionCard
              title="Decision distribution"
              description="Counts of backend decision actions in the selected range."
              actions={<Badge tone="purple">Server history</Badge>}
            >
              <DecisionDistributionChart
                decisions={rawDecisions}
                emptyTitle={emptyTitle}
                emptyDescription={emptyDescription}
              />
            </SectionCard>

            <SectionCard
              title="Anomaly and decision co-occurrence"
              description="Backend anomaly assessments joined to decisions by shared monitoring window sequence."
            >
              {correlationCounts.length === 0 ? (
                <EmptyState
                  compact
                  title="No paired records in this range"
                  description="Pairs appear only when anomaly and decision history share a window sequence."
                />
              ) : (
                <div className="table-shell">
                  <table className="data-table">
                    <caption className="sr-only">
                      Anomaly assessment categories and decision action counts
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">Assessment</th>
                        <th scope="col">Increase</th>
                        <th scope="col">Decrease</th>
                        <th scope="col">Keep</th>
                        <th scope="col">Unknown</th>
                        <th scope="col">Total</th>
                      </tr>
                    </thead>
                    <tbody>
                      {correlationCounts.map((count) => (
                        <tr key={count.category}>
                          <td className="font-medium text-slate-800">
                            {titleCase(count.category)}
                          </td>
                          <td>{formatInteger(count.INCREASE_LIMIT)}</td>
                          <td>{formatInteger(count.DECREASE_LIMIT)}</td>
                          <td>{formatInteger(count.KEEP_LIMIT)}</td>
                          <td>{formatInteger(count.UNKNOWN)}</td>
                          <td>{formatInteger(count.total)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <Notice tone="info" className="mt-4">
                These counts describe co-occurrence only. Shared timestamps and
                window sequences do not establish causation.
              </Notice>
            </SectionCard>
          </div>

          <Notice tone="info" title="Decision data scope">
            The tables below use the backend's bounded history. Proposed and
            applied limits are separate backend-reported values.
          </Notice>

          <SectionCard
            title="Anomaly decision pairs"
            description="Each backend decision paired with an anomaly assessment from the same window sequence when one is available."
            noBodyPadding
          >
            {correlationRows.length === 0 ? (
              <div className="p-5 sm:p-6">
                <EmptyState
                  icon={<GitBranch aria-hidden="true" className="h-5 w-5" />}
                  title={emptyTitle ?? "No paired records yet"}
                  description={
                    emptyDescription ??
                    "Wait for the backend to complete an adaptive evaluation with both an anomaly assessment and a decision."
                  }
                />
              </div>
            ) : (
              <div className="table-shell max-h-[28rem] overflow-y-auto">
                <table className="data-table">
                  <caption className="sr-only">
                    Anomaly assessment and adaptive decision pairs
                  </caption>
                  <thead className="sticky top-0 z-10">
                    <tr>
                      <th scope="col">Evaluated</th>
                      <th scope="col">Window</th>
                      <th scope="col">Anomaly</th>
                      <th scope="col">Score</th>
                      <th scope="col">Decision</th>
                      <th scope="col">Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...correlationRows].reverse().map((row) => (
                      <tr key={row.key}>
                        <td>{formatDateTime(row.evaluatedAt)}</td>
                        <td>{formatInteger(row.windowSequence)}</td>
                        <td>
                          {!row.anomaly
                            ? "Not reported"
                            : !row.anomaly.available
                              ? "Unavailable"
                              : row.anomaly.isAnomaly
                                ? "Anomaly"
                                : "Normal"}
                        </td>
                        <td className="font-mono">
                          {formatDecimal(row.anomaly?.anomalyScore ?? null)}
                        </td>
                        <td>{titleCase(normalizeDecisionAction(row.decision.action))}</td>
                        <td className="font-mono text-xs text-slate-600">
                          {row.decision.reasonCode ?? "-"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </SectionCard>

          <SectionCard
            title="Observed decisions"
            description="Every decision retained by the backend in the selected range, newest first."
            noBodyPadding
          >
            {recentDecisions.length === 0 ? (
              <div className="p-5 sm:p-6">
                <EmptyState
                  icon={<GitBranch aria-hidden="true" className="h-5 w-5" />}
                  title={emptyTitle ?? "No decisions observed yet"}
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
                    Adaptive decisions in the selected time range
                  </caption>
                  <thead className="sticky top-0 z-10">
                    <tr>
                      <th scope="col">Evaluated</th>
                      <th scope="col">Window</th>
                      <th scope="col">Action</th>
                      <th scope="col">Limit change</th>
                      <th scope="col">Reason</th>
                      <th scope="col">Explanation</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentDecisions.map((decision) => (
                      <tr key={decision.key}>
                        <td>{formatDateTime(decision.evaluatedAt)}</td>
                        <td>{formatInteger(decision.windowSequence)}</td>
                        <td>
                          <DecisionBadge decision={decision} />
                        </td>
                        <td>
                          <div className="flex flex-col gap-0.5">
                            <span className="inline-flex items-center gap-1.5 font-semibold text-slate-800">
                              {formatInteger(decision.previousLimit)}
                              <ArrowRight
                                aria-hidden="true"
                                className="h-3.5 w-3.5 text-slate-400"
                              />
                              {formatInteger(decision.appliedLimit)}
                            </span>
                            <span className="text-[11px] text-slate-400">
                              Proposed {formatInteger(decision.proposedLimit)}
                            </span>
                          </div>
                        </td>
                        <td>
                          <span className="font-mono text-xs text-slate-600">
                            {decision.reasonCode ?? "-"}
                          </span>
                        </td>
                        <td className="max-w-[28rem] whitespace-normal text-xs leading-5 text-slate-500">
                          {decision.explanation ?? "-"}
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
