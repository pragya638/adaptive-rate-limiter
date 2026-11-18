import { AlertTriangle, CheckCircle2, Info, ShieldAlert } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "../../lib/cn";

export type NoticeTone = "info" | "warning" | "danger" | "success";

const toneClasses: Record<NoticeTone, string> = {
  info: "border-brand-200 bg-brand-50/70 text-brand-800",
  warning: "border-amber-200 bg-amber-50/80 text-amber-900",
  danger: "border-rose-200 bg-rose-50/80 text-rose-900",
  success: "border-emerald-200 bg-emerald-50/80 text-emerald-900",
};

const iconClasses: Record<NoticeTone, string> = {
  info: "text-brand-600",
  warning: "text-amber-600",
  danger: "text-rose-600",
  success: "text-emerald-600",
};

function ToneIcon({ tone }: { tone: NoticeTone }) {
  if (tone === "warning") {
    return <AlertTriangle aria-hidden="true" className="h-4 w-4" />;
  }
  if (tone === "danger") {
    return <ShieldAlert aria-hidden="true" className="h-4 w-4" />;
  }
  if (tone === "success") {
    return <CheckCircle2 aria-hidden="true" className="h-4 w-4" />;
  }
  return <Info aria-hidden="true" className="h-4 w-4" />;
}

interface NoticeProps {
  tone?: NoticeTone;
  title?: string;
  children: ReactNode;
  className?: string;
}

export function Notice({
  tone = "info",
  title,
  children,
  className,
}: NoticeProps) {
  return (
    <div
      className={cn(
        "flex gap-3 rounded-xl border px-4 py-3 text-xs leading-5",
        toneClasses[tone],
        className,
      )}
    >
      <span className={cn("mt-0.5 shrink-0", iconClasses[tone])}>
        <ToneIcon tone={tone} />
      </span>
      <div>
        {title ? <p className="font-semibold">{title}</p> : null}
        <div className={title ? "mt-0.5" : ""}>{children}</div>
      </div>
    </div>
  );
}
