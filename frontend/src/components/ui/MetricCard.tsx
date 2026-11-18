import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "../../lib/cn";

export type MetricTone = "brand" | "emerald" | "amber" | "rose" | "violet" | "slate";

const toneClasses: Record<MetricTone, string> = {
  brand: "bg-brand-50 text-brand-700 ring-brand-100",
  emerald: "bg-emerald-50 text-emerald-700 ring-emerald-100",
  amber: "bg-amber-50 text-amber-700 ring-amber-100",
  rose: "bg-rose-50 text-rose-700 ring-rose-100",
  violet: "bg-violet-50 text-violet-700 ring-violet-100",
  slate: "bg-slate-100 text-slate-600 ring-slate-200",
};

interface MetricCardProps {
  label: string;
  value: ReactNode;
  helper?: ReactNode;
  icon: LucideIcon;
  tone?: MetricTone;
  unavailable?: boolean;
  className?: string;
}

export function MetricCard({
  label,
  value,
  helper,
  icon: Icon,
  tone = "brand",
  unavailable = false,
  className,
}: MetricCardProps) {
  return (
    <div className={cn("card p-5", className)}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          {label}
        </p>
        <span
          className={cn(
            "flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ring-1",
            unavailable ? toneClasses.slate : toneClasses[tone],
          )}
        >
          <Icon aria-hidden="true" className="h-4 w-4" />
        </span>
      </div>
      <div
        className={cn(
          "mt-3 text-2xl font-bold tracking-tight",
          unavailable ? "text-slate-400" : "text-slate-950",
        )}
      >
        {value}
      </div>
      {helper ? (
        <div className="mt-1.5 text-xs leading-5 text-slate-500">{helper}</div>
      ) : null}
    </div>
  );
}
