import { RefreshCw, ServerOff } from "lucide-react";

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  isRetrying?: boolean;
}

export function ErrorState({
  title = "Unable to load this view",
  message = "The backend did not return the data required for this page. Check that the Spring Boot service is running and try again.",
  onRetry,
  isRetrying = false,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex min-h-[320px] flex-col items-center justify-center gap-3 rounded-2xl border border-rose-200 bg-white p-8 text-center shadow-card"
    >
      <div className="flex h-11 w-11 items-center justify-center rounded-full bg-rose-50 text-rose-600 ring-1 ring-rose-100">
        <ServerOff aria-hidden="true" className="h-5 w-5" />
      </div>
      <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
      <p className="max-w-md text-xs leading-5 text-slate-500">{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          disabled={isRetrying}
          className="btn-secondary mt-2"
        >
          <RefreshCw
            aria-hidden="true"
            className={`h-4 w-4 ${isRetrying ? "animate-spin" : ""}`}
          />
          {isRetrying ? "Retrying…" : "Retry now"}
        </button>
      ) : null}
    </div>
  );
}
