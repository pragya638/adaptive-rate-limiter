import { Activity, BrainCircuit } from "lucide-react";

import type { SystemStatus } from "../../api/types";
import { formatDateTime, formatInteger, titleCase } from "../../lib/format";
import { Badge, type BadgeTone } from "../ui/Badge";
import { EmptyState } from "../ui/EmptyState";
import { SectionCard } from "../ui/SectionCard";

function availabilityState(ml: SystemStatus["ml"] | undefined) {
  if (!ml || ml.status === "disabled") {
    return { state: "Disabled", tone: "neutral" as BadgeTone };
  }
  if (!ml.available) {
    return { state: "Unavailable", tone: "warning" as BadgeTone };
  }
  if (!ml.modelReady) {
    return { state: "Degraded", tone: "warning" as BadgeTone };
  }
  return { state: "Operational", tone: "success" as BadgeTone };
}

export function MlServiceStatusCard({
  systemStatus,
}: {
  systemStatus: SystemStatus | undefined;
}) {
  const ml = systemStatus?.ml;
  const availability = availabilityState(ml);

  return (
    <SectionCard
      title="ML service status"
      description="Backend-reported health and evaluation timing for the anomaly service."
      actions={
        <Badge tone={availability.tone} dot>
          {availability.state}
        </Badge>
      }
    >
      {!ml ? (
        <EmptyState
          compact
          icon={<BrainCircuit aria-hidden="true" className="h-5 w-5" />}
          title="ML status unavailable"
          description="The backend did not include ML health in the system-status snapshot."
        />
      ) : (
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-xs font-medium text-slate-500">Model</dt>
            <dd className="mt-1 font-semibold text-slate-900">
              {ml.model ?? "—"} {ml.version ? `v${ml.version}` : ""}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">Model ready</dt>
            <dd className="mt-1 font-semibold text-slate-900">
              {ml.modelReady ? "Yes" : "No"}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">
              Last successful evaluation
            </dt>
            <dd className="mt-1 font-semibold text-slate-900">
              {formatDateTime(ml.lastSuccessfulEvaluationAt)}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">
              Health check latency
            </dt>
            <dd className="mt-1 font-semibold text-slate-900">
              {ml.latencyMs === null || ml.latencyMs === undefined
                ? "—"
                : `${formatInteger(ml.latencyMs)} ms`}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">Last failure</dt>
            <dd className="mt-1 font-semibold text-slate-900">
              {formatDateTime(ml.lastFailureAt)}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-slate-500">Status detail</dt>
            <dd className="mt-1 text-xs leading-5 text-slate-600">
              {ml.detail ?? titleCase(ml.status)}
            </dd>
          </div>
        </dl>
      )}
      {ml?.lastFailureDetail ? (
        <p className="mt-4 flex gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900 ring-1 ring-amber-200">
          <Activity aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          {ml.lastFailureDetail}
        </p>
      ) : null}
    </SectionCard>
  );
}
