import { ArrowRight, CircleHelp, GitBranch } from "lucide-react";

import type { AdaptiveStatus } from "../../api/types";
import { decisionActionTone } from "../../lib/decision";
import {
  formatDateTime,
  formatInteger,
  titleCase,
} from "../../lib/format";
import { Badge } from "../ui/Badge";
import { EmptyState } from "../ui/EmptyState";
import { SectionCard } from "../ui/SectionCard";

export function DecisionSummaryCard({
  status,
}: {
  status: AdaptiveStatus | undefined;
}) {
  const decision = status?.lastDecision;

  if (!status?.enabled) {
    return (
      <SectionCard
        title="Latest adaptive decision"
        description="The most recent controller output reported by the backend."
      >
        <EmptyState
          compact
          icon={<CircleHelp aria-hidden="true" className="h-5 w-5" />}
          title="Adaptive mode is inactive"
          description="No adaptive decisions are produced while the backend is in static mode."
        />
      </SectionCard>
    );
  }

  if (!decision) {
    return (
      <SectionCard
        title="Latest adaptive decision"
        description="The most recent controller output reported by the backend."
      >
        <EmptyState
          compact
          icon={<GitBranch aria-hidden="true" className="h-5 w-5" />}
          title="No decision yet"
          description="Adaptive mode is enabled, but the backend has not reported a controller decision yet."
        />
      </SectionCard>
    );
  }

  return (
    <SectionCard
      title="Latest adaptive decision"
      description="The most recent controller output reported by the backend."
      actions={
        <Badge tone={decisionActionTone(decision.action)} dot>
          {titleCase(decision.action)}
        </Badge>
      }
    >
      <div className="flex items-center gap-3 rounded-xl bg-slate-50 px-4 py-3 ring-1 ring-slate-200">
        <div className="text-center">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">
            Previous
          </p>
          <p className="mt-0.5 text-lg font-bold text-slate-700">
            {formatInteger(decision.previousLimit)}
          </p>
        </div>
        <ArrowRight aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-400" />
        <div className="text-center">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">
            Applied
          </p>
          <p className="mt-0.5 text-lg font-bold text-slate-950">
            {formatInteger(decision.appliedLimit)}
          </p>
        </div>
        <div className="ml-auto text-right">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">
            Evaluated
          </p>
          <p className="mt-0.5 text-xs font-medium text-slate-600">
            {formatDateTime(decision.evaluatedAt)}
          </p>
        </div>
      </div>
      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-xs font-medium text-slate-500">Reason code</dt>
          <dd className="mt-0.5 font-mono text-xs font-semibold text-slate-800">
            {decision.reasonCode ?? "-"}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-slate-500">Current limit</dt>
          <dd className="mt-0.5 font-semibold text-slate-900">
            {formatInteger(status.currentLimit)} requests / window
          </dd>
        </div>
      </dl>
      <p className="mt-3 text-xs leading-5 text-slate-500">
        {decision.explanation ?? "No explanation was supplied by the backend."}
      </p>
    </SectionCard>
  );
}
