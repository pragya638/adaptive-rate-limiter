import { Activity, CircleHelp, ShieldCheck, ShieldX } from "lucide-react";

import type { AdaptiveStatus } from "../../api/types";
import {
  EMPTY_VALUE,
  formatDateTime,
  formatDecimal,
  titleCase,
} from "../../lib/format";
import { Badge } from "../ui/Badge";
import { EmptyState } from "../ui/EmptyState";
import { SectionCard } from "../ui/SectionCard";

export function AnomalySummaryCard({
  status,
}: {
  status: AdaptiveStatus | undefined;
}) {
  const anomaly = status?.latestAnomaly;

  if (!status?.enabled) {
    return (
      <SectionCard
        title="Latest anomaly assessment"
        description="Isolation Forest output from the latest adaptive evaluation."
      >
        <EmptyState
          compact
          icon={<CircleHelp aria-hidden="true" className="h-5 w-5" />}
          title="Adaptive mode is inactive"
          description="The backend is running in static mode, so no anomaly assessments are produced."
        />
      </SectionCard>
    );
  }

  if (!anomaly) {
    return (
      <SectionCard
        title="Latest anomaly assessment"
        description="Isolation Forest output from the latest adaptive evaluation."
      >
        <EmptyState
          compact
          icon={<Activity aria-hidden="true" className="h-5 w-5" />}
          title="No assessment yet"
          description="Adaptive mode is enabled, but the backend has not completed an evaluation window yet."
        />
      </SectionCard>
    );
  }

  if (!anomaly.available) {
    return (
      <SectionCard
        title="Latest anomaly assessment"
        description="Isolation Forest output from the latest adaptive evaluation."
        actions={<Badge tone="warning" dot>ML unavailable</Badge>}
      >
        <EmptyState
          compact
          icon={<CircleHelp aria-hidden="true" className="h-5 w-5" />}
          title="Assessment unavailable"
          description="The ML assessment was not available for the latest evaluation. The backend may have applied a fallback decision."
        />
      </SectionCard>
    );
  }

  const isAnomaly = anomaly.isAnomaly === true;

  return (
    <SectionCard
      title="Latest anomaly assessment"
      description="Isolation Forest output from the latest adaptive evaluation."
      actions={
        <Badge tone={isAnomaly ? "danger" : "success"} dot>
          {isAnomaly ? "Anomaly" : "Normal"}
        </Badge>
      }
    >
      <div className="flex items-start gap-4">
        <span
          className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl ring-1 ${
            isAnomaly
              ? "bg-rose-50 text-rose-600 ring-rose-100"
              : "bg-emerald-50 text-emerald-600 ring-emerald-100"
          }`}
        >
          {isAnomaly ? (
            <ShieldX aria-hidden="true" className="h-6 w-6" />
          ) : (
            <ShieldCheck aria-hidden="true" className="h-6 w-6" />
          )}
        </span>
        <dl className="grid min-w-0 flex-1 grid-cols-2 gap-x-4 gap-y-3 text-sm">
          <div>
            <dt className="text-xs font-medium text-slate-500">Prediction</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">
              {titleCase(anomaly.prediction)}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">Anomaly score</dt>
            <dd className="mt-0.5 font-mono font-semibold text-slate-900">
              {formatDecimal(anomaly.anomalyScore)}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">Model</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">
              {anomaly.model ?? EMPTY_VALUE}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">
              Evaluated at
            </dt>
            <dd className="mt-0.5 text-slate-700">
              {formatDateTime(status.lastEvaluationAt)}
            </dd>
          </div>
        </dl>
      </div>
      <p className="mt-4 border-t border-slate-100 pt-3 text-[11px] leading-5 text-slate-400">
        The Isolation Forest score is not a calibrated probability. Higher values
        indicate more anomalous traffic.
      </p>
    </SectionCard>
  );
}
