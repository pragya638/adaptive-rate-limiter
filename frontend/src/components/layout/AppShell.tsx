import { Menu } from "lucide-react";
import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { useDashboardData } from "../../hooks/useDashboardData";
import { cn } from "../../lib/cn";
import { LastUpdated } from "../ui/LastUpdated";
import { ModeBadge } from "../ui/ModeBadge";
import { OfflineBanner } from "../dashboard/OfflineBanner";
import { pageMeta } from "./navigation";
import { Sidebar } from "./Sidebar";

export function AppShell() {
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const { metricsQuery, statusQuery, healthQuery } = useDashboardData();

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  const meta = pageMeta[location.pathname] ?? {
    title: "Dashboard",
    section: "Operations",
  };

  const connectionState = healthQuery.isError
    ? "offline"
    : healthQuery.isSuccess
      ? "connected"
      : "connecting";

  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />

      <div className="lg:pl-72">
        <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/90 backdrop-blur">
          <div className="flex min-h-16 items-center gap-3 px-4 sm:px-6 lg:px-8">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 focus-ring lg:hidden"
              aria-label="Open navigation"
              aria-expanded={mobileOpen}
            >
              <Menu aria-hidden="true" className="h-4 w-4" />
            </button>

            <div className="min-w-0 flex-1">
              <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400">
                {meta.section}
              </p>
              <p className="truncate text-sm font-semibold text-slate-800">
                {meta.title}
              </p>
            </div>

            <div className="hidden items-center gap-2 sm:flex">
              <span
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold",
                  connectionState === "connected" &&
                    "border-emerald-200 bg-emerald-50 text-emerald-700",
                  connectionState === "offline" &&
                    "border-rose-200 bg-rose-50 text-rose-700",
                  connectionState === "connecting" &&
                    "border-slate-200 bg-slate-50 text-slate-500",
                )}
              >
                <span
                  className={cn(
                    "h-1.5 w-1.5 rounded-full",
                    connectionState === "connected" && "bg-emerald-500",
                    connectionState === "offline" && "bg-rose-500",
                    connectionState === "connecting" && "bg-slate-400",
                  )}
                />
                {connectionState === "connected"
                  ? "Backend connected"
                  : connectionState === "offline"
                    ? "Backend offline"
                    : "Connecting"}
              </span>
              <ModeBadge mode={statusQuery.data?.mode} />
            </div>

            <LastUpdated
              dataUpdatedAt={metricsQuery.dataUpdatedAt}
              isFetching={metricsQuery.isFetching}
              isError={metricsQuery.isError}
            />
          </div>
        </header>

        {healthQuery.isError ? (
          <OfflineBanner error={healthQuery.error ?? null} />
        ) : null}

        <main className="mx-auto w-full max-w-[1500px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
