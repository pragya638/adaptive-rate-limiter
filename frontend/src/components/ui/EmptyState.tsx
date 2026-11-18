import { Inbox } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "../../lib/cn";

interface EmptyStateProps {
  title: string;
  description: string;
  icon?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
}

export function EmptyState({
  title,
  description,
  icon,
  action,
  compact = false,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50/60 text-center",
        compact ? "px-4 py-8" : "px-6 py-12",
      )}
    >
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white text-slate-400 shadow-sm ring-1 ring-slate-200">
        {icon ?? <Inbox aria-hidden="true" className="h-5 w-5" />}
      </div>
      <h3 className="mt-3 text-sm font-semibold text-slate-800">{title}</h3>
      <p className="mt-1 max-w-md text-xs leading-5 text-slate-500">
        {description}
      </p>
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
