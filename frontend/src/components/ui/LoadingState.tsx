import { Loader2 } from "lucide-react";

export function LoadingState({
  label = "Loading live backend data…",
}: {
  label?: string;
}) {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-live="polite"
      className="flex min-h-[320px] flex-col items-center justify-center gap-3 rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-card"
    >
      <Loader2
        aria-hidden="true"
        className="h-7 w-7 animate-spin text-brand-600"
      />
      <p className="text-sm font-medium text-slate-600">{label}</p>
      <p className="max-w-sm text-xs text-slate-400">
        Contacting the read-only observability endpoints. This may take a moment
        if the backend is starting.
      </p>
    </div>
  );
}

export function InlineLoading({ label = "Loading…" }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-xs text-slate-500">
      <Loader2 aria-hidden="true" className="h-3.5 w-3.5 animate-spin" />
      {label}
    </span>
  );
}
