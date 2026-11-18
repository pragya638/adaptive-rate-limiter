import type { ReactNode } from "react";

import { cn } from "../../lib/cn";

export type BadgeTone =
  | "neutral"
  | "info"
  | "success"
  | "warning"
  | "danger"
  | "purple";

const toneClasses: Record<BadgeTone, string> = {
  neutral: "border-slate-200 bg-slate-50 text-slate-600",
  info: "border-brand-200 bg-brand-50 text-brand-700",
  success: "border-emerald-200 bg-emerald-50 text-emerald-700",
  warning: "border-amber-200 bg-amber-50 text-amber-700",
  danger: "border-rose-200 bg-rose-50 text-rose-700",
  purple: "border-violet-200 bg-violet-50 text-violet-700",
};

const dotClasses: Record<BadgeTone, string> = {
  neutral: "bg-slate-400",
  info: "bg-brand-500",
  success: "bg-emerald-500",
  warning: "bg-amber-500",
  danger: "bg-rose-500",
  purple: "bg-violet-500",
};

interface BadgeProps {
  tone?: BadgeTone;
  children: ReactNode;
  dot?: boolean;
  className?: string;
}

export function Badge({
  tone = "neutral",
  children,
  dot = false,
  className,
}: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold",
        toneClasses[tone],
        className,
      )}
    >
      {dot ? (
        <span className={cn("h-1.5 w-1.5 rounded-full", dotClasses[tone])} />
      ) : null}
      {children}
    </span>
  );
}
