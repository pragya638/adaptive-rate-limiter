import {
  FlaskConical,
  Gauge,
  Loader2,
  Play,
  Square,
  ShieldCheck,
  ShieldX,
  WifiOff,
} from "lucide-react";
import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";

import { api, apiErrorMessage } from "../../api/client";
import type { DemoResponse } from "../../api/types";
import { cn } from "../../lib/cn";
import { Badge } from "../ui/Badge";
import { EmptyState } from "../ui/EmptyState";
import { Notice } from "../ui/Notice";
import { SectionCard } from "../ui/SectionCard";

const CLIENT_ID_PATTERN = /^[A-Za-z0-9._:-]{1,64}$/;
const MIN_REQUESTS = 1;
const MAX_REQUESTS = 100;

type ResultOutcome =
  | "allowed"
  | "rejected"
  | "other"
  | "network"
  | "cancelled";

interface TestResult {
  requestNumber: number;
  outcome: ResultOutcome;
  status: number | null;
  statusText: string;
  body: string;
  contentType: string | null;
  latencyMs: number;
  rateLimitLimit: string | null;
  rateLimitRemaining: string | null;
  retryAfter: string | null;
  errorMessage: string | null;
}

interface RequestTesterProps {
  currentLimit?: number;
  sendRequest?: (
    clientId: string,
    signal?: AbortSignal,
  ) => Promise<DemoResponse>;
  onRunComplete?: () => void;
}

function resultFromResponse(
  requestNumber: number,
  response: DemoResponse,
): TestResult {
  const outcome: ResultOutcome =
    response.status === 200
      ? "allowed"
      : response.status === 429
        ? "rejected"
        : "other";

  return {
    requestNumber,
    outcome,
    status: response.status,
    statusText: response.statusText,
    body: response.body,
    contentType: response.contentType,
    latencyMs: response.latencyMs,
    rateLimitLimit: response.rateLimitLimit,
    rateLimitRemaining: response.rateLimitRemaining,
    retryAfter: response.retryAfter,
    errorMessage: null,
  };
}

function formatBody(result: TestResult): string {
  if (result.errorMessage) {
    return result.errorMessage;
  }
  const body = result.body.trim();
  if (!body) {
    return "(empty response body)";
  }
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    return result.body;
  }
}

function OutcomeBadge({ result }: { result: TestResult }) {
  if (result.outcome === "allowed") {
    return (
      <Badge tone="success" dot>
        HTTP 200 Allowed
      </Badge>
    );
  }
  if (result.outcome === "rejected") {
    return (
      <Badge tone="danger" dot>
        HTTP 429 Rejected
      </Badge>
    );
  }
  if (result.outcome === "network") {
    return (
      <Badge tone="warning" dot>
        Network failure
      </Badge>
    );
  }
  if (result.outcome === "cancelled") {
    return (
      <Badge tone="neutral" dot>
        Stopped
      </Badge>
    );
  }
  return (
    <Badge tone="warning" dot>
      HTTP {result.status}
    </Badge>
  );
}

export function RequestTester({
  currentLimit,
  sendRequest,
  onRunComplete,
}: RequestTesterProps) {
  const [clientId, setClientId] = useState("dashboard-test");
  const [requestCount, setRequestCount] = useState("10");
  const [formError, setFormError] = useState<string | null>(null);
  const [results, setResults] = useState<TestResult[]>([]);
  const [isRunning, setIsRunning] = useState(false);
  const [progress, setProgress] = useState({ completed: 0, total: 0 });

  const runIdRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      runIdRef.current += 1;
      abortRef.current?.abort();
    };
  }, []);

  const allowed = results.filter(
    (result) => result.outcome === "allowed",
  ).length;
  const rejected = results.filter(
    (result) => result.outcome === "rejected",
  ).length;
  const other = results.filter((result) => result.outcome === "other").length;
  const failed = results.filter(
    (result) =>
      result.outcome === "network" || result.outcome === "cancelled",
  ).length;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedClientId = clientId.trim();
    const count = Number(requestCount);

    if (!CLIENT_ID_PATTERN.test(trimmedClientId)) {
      setFormError(
        "Client ID must be 1-64 characters using letters, numbers, dots, underscores, colons, or hyphens.",
      );
      return;
    }
    if (
      !Number.isInteger(count) ||
      count < MIN_REQUESTS ||
      count > MAX_REQUESTS
    ) {
      setFormError(
        `Request count must be a whole number from ${MIN_REQUESTS} to ${MAX_REQUESTS}.`,
      );
      return;
    }

    setFormError(null);

    const runId = runIdRef.current + 1;
    runIdRef.current = runId;
    const controller = new AbortController();
    abortRef.current = controller;

    setResults([]);
    setIsRunning(true);
    setProgress({ completed: 0, total: count });

    try {
      for (let requestNumber = 1; requestNumber <= count; requestNumber++) {
        if (controller.signal.aborted) {
          break;
        }

        const startedAt = performance.now();
        let shouldStop = false;

        try {
          const response = await (sendRequest ?? api.demoRequest)(
            trimmedClientId,
            controller.signal,
          );
          if (runIdRef.current !== runId) {
            return;
          }
          setResults((current) => [
            ...current,
            resultFromResponse(requestNumber, response),
          ]);
        } catch (error) {
          if (runIdRef.current !== runId) {
            return;
          }
          const wasCancelled = controller.signal.aborted;
          setResults((current) => [
            ...current,
            {
              requestNumber,
              outcome: wasCancelled ? "cancelled" : "network",
              status: null,
              statusText: "",
              body: "",
              contentType: null,
              latencyMs: performance.now() - startedAt,
              rateLimitLimit: null,
              rateLimitRemaining: null,
              retryAfter: null,
              errorMessage: wasCancelled
                ? "Request stopped before a response was received."
                : apiErrorMessage(error),
            },
          ]);
          shouldStop = true;
        }

        setProgress({ completed: requestNumber, total: count });
        if (shouldStop) {
          break;
        }
      }
    } finally {
      if (runIdRef.current === runId) {
        setIsRunning(false);
        abortRef.current = null;
        onRunComplete?.();
      }
    }
  };

  const handleStop = () => {
    abortRef.current?.abort();
  };

  return (
    <SectionCard
      title="Request tester"
      description={`Send real requests to the protected /api/demo endpoint. Each request uses the X-Client-Id header and is counted by the backend.${currentLimit === undefined ? "" : ` The backend currently reports a limit of ${currentLimit} request(s) per window.`}`}
      actions={<Badge tone="info">Live API</Badge>}
    >
      <form
        noValidate
        onSubmit={handleSubmit}
        aria-busy={isRunning}
        className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_10rem_auto] sm:items-end"
      >
        <label className="block text-sm">
          <span className="text-xs font-medium text-slate-600">Client ID</span>
          <input
            type="text"
            aria-label="Client ID"
            value={clientId}
            onChange={(event) => setClientId(event.target.value)}
            disabled={isRunning}
            autoComplete="off"
            spellCheck={false}
            className="mt-1.5 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 font-mono text-sm text-slate-800 shadow-sm focus-ring disabled:cursor-not-allowed disabled:bg-slate-50"
          />
          <span className="mt-1 block text-[11px] text-slate-400">
            Sent as <span className="font-mono">X-Client-Id</span>. Use a new ID
            for a fresh limiter bucket.
          </span>
        </label>

        <label className="block text-sm">
          <span className="text-xs font-medium text-slate-600">
            Request count
          </span>
          <input
            type="number"
            aria-label="Request count"
            inputMode="numeric"
            min={MIN_REQUESTS}
            max={MAX_REQUESTS}
            step={1}
            value={requestCount}
            onChange={(event) => setRequestCount(event.target.value)}
            disabled={isRunning}
            className="mt-1.5 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm focus-ring disabled:cursor-not-allowed disabled:bg-slate-50"
          />
          <span className="mt-1 block text-[11px] text-slate-400">
            1-{MAX_REQUESTS} sequential requests.
          </span>
        </label>

        <div className="flex items-center gap-2">
          <button
            type="submit"
            disabled={isRunning}
            className="tab-button bg-brand-600 text-white shadow-sm hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isRunning ? (
              <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" />
            ) : (
              <Play aria-hidden="true" className="h-4 w-4" />
            )}
            {isRunning ? "Sending" : "Run test"}
          </button>
          <button
            type="button"
            onClick={handleStop}
            disabled={!isRunning}
            className="btn-secondary"
          >
            <Square aria-hidden="true" className="h-4 w-4" />
            Stop
          </button>
        </div>
      </form>

      {formError ? (
        <Notice tone="danger" title="Check the test inputs" className="mt-4">
          {formError}
        </Notice>
      ) : null}

      {isRunning ? (
        <div
          role="status"
          aria-live="polite"
          className="mt-4 flex items-center gap-2 rounded-xl border border-brand-200 bg-brand-50/70 px-4 py-3 text-xs text-brand-800"
        >
          <Loader2
            aria-hidden="true"
            className="h-4 w-4 shrink-0 animate-spin"
          />
          <span>
            Sending request{" "}
            {Math.min(progress.completed + 1, progress.total)} of{" "}
            {progress.total} for{" "}
            <span className="font-mono font-semibold">{clientId.trim()}</span>.
          </span>
        </div>
      ) : null}

      {results.length > 0 ? (
        <div className="mt-5 space-y-4">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-xl border border-emerald-200 bg-emerald-50/70 p-3">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-700">
                <ShieldCheck aria-hidden="true" className="h-3.5 w-3.5" />
                Allowed
              </p>
              <p
                className="mt-1 text-xl font-bold text-emerald-900"
                data-testid="request-tester-allowed"
              >
                {allowed}
              </p>
              <p className="text-[11px] text-emerald-700">HTTP 200</p>
            </div>
            <div className="rounded-xl border border-rose-200 bg-rose-50/70 p-3">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-rose-700">
                <ShieldX aria-hidden="true" className="h-3.5 w-3.5" />
                Rejected
              </p>
              <p
                className="mt-1 text-xl font-bold text-rose-900"
                data-testid="request-tester-rejected"
              >
                {rejected}
              </p>
              <p className="text-[11px] text-rose-700">HTTP 429</p>
            </div>
            <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-3">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-amber-700">
                <WifiOff aria-hidden="true" className="h-3.5 w-3.5" />
                Network / stopped
              </p>
              <p
                className="mt-1 text-xl font-bold text-amber-900"
                data-testid="request-tester-failed"
              >
                {failed}
              </p>
              <p className="text-[11px] text-amber-700">No HTTP response</p>
            </div>
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-slate-600">
                <FlaskConical aria-hidden="true" className="h-3.5 w-3.5" />
                Other HTTP
              </p>
              <p
                className="mt-1 text-xl font-bold text-slate-900"
                data-testid="request-tester-other"
              >
                {other}
              </p>
              <p className="text-[11px] text-slate-500">
                Completed: {results.length} of {progress.total}
              </p>
            </div>
          </div>

          <p className="text-xs leading-5 text-slate-500">
            These run totals are calculated only from the responses above. The
            metric cards higher on this page are backend cumulative counters and
            update after the next metrics poll.
          </p>

          <div className="max-h-[30rem] overflow-auto rounded-xl border border-slate-200">
            <table className="data-table">
              <thead className="sticky top-0 z-10">
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">Response</th>
                  <th scope="col">Latency</th>
                  <th scope="col">Limit</th>
                  <th scope="col">Remaining</th>
                  <th scope="col">Retry-After</th>
                  <th scope="col">Response body</th>
                </tr>
              </thead>
              <tbody>
                {results.map((result) => (
                  <tr
                    key={result.requestNumber}
                    data-testid={`request-result-${result.requestNumber}`}
                    className={cn(
                      result.outcome === "allowed" && "bg-emerald-50/40",
                      result.outcome === "rejected" && "bg-rose-50/50",
                      (result.outcome === "network" ||
                        result.outcome === "cancelled") &&
                        "bg-amber-50/50",
                    )}
                  >
                    <td className="font-mono text-xs">{result.requestNumber}</td>
                    <td>
                      <span title={result.statusText}>
                        <OutcomeBadge result={result} />
                      </span>
                    </td>
                    <td className="font-mono text-xs">
                      {Math.round(result.latencyMs)} ms
                    </td>
                    <td className="font-mono text-xs">
                      {result.rateLimitLimit ?? "-"}
                    </td>
                    <td className="font-mono text-xs">
                      {result.rateLimitRemaining ?? "-"}
                    </td>
                    <td className="font-mono text-xs">
                      {result.retryAfter ?? "-"}
                    </td>
                    <td className="max-w-[24rem] whitespace-normal">
                      {result.errorMessage ? (
                        <span className="text-xs text-rose-700">
                          {result.errorMessage}
                        </span>
                      ) : (
                        <details>
                          <summary className="cursor-pointer text-xs font-medium text-brand-700">
                            View body
                          </summary>
                          {result.contentType ? (
                            <p className="mt-1 text-[10px] text-slate-400">
                              {result.contentType}
                            </p>
                          ) : null}
                          <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-slate-950 p-3 font-mono text-[11px] leading-5 text-slate-100">
                            {formatBody(result)}
                          </pre>
                        </details>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : !isRunning ? (
        <div className="mt-5">
          <EmptyState
            compact
            icon={<Gauge aria-hidden="true" className="h-5 w-5" />}
            title="No test requests yet"
            description="Choose a client ID and request count, then run the test. Results show the actual status, headers, body, and latency returned by the backend."
          />
        </div>
      ) : null}
    </SectionCard>
  );
}
