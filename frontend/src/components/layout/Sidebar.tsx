import { Activity, X } from "lucide-react";
import { NavLink } from "react-router-dom";

import { cn } from "../../lib/cn";
import { navigationGroups } from "./navigation";

interface SidebarProps {
  mobileOpen: boolean;
  onClose: () => void;
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col bg-ink-950 text-slate-300">
      <div className="flex items-start gap-3 px-5 pb-5 pt-6">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-500 text-white shadow-lg shadow-brand-950/30">
          <Activity aria-hidden="true" className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-brand-300">
            Read-only dashboard
          </p>
          <p className="mt-1 text-sm font-semibold leading-5 text-white">
            AI-Driven Adaptive API Rate Limiter
          </p>
        </div>
      </div>

      <nav
        aria-label="Dashboard navigation"
        className="flex-1 space-y-6 overflow-y-auto px-3 pb-4"
      >
        {navigationGroups.map((group) => (
          <div key={group.label}>
            <p className="px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500">
              {group.label}
            </p>
            <div className="space-y-1">
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        "group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400",
                        isActive
                          ? "bg-white/10 text-white shadow-sm ring-1 ring-white/10"
                          : "text-slate-400 hover:bg-white/5 hover:text-slate-100",
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <Icon
                          aria-hidden="true"
                          className={cn(
                            "h-4 w-4 shrink-0",
                            isActive ? "text-brand-300" : "text-slate-500",
                          )}
                        />
                        <span className="min-w-0">
                          <span className="block truncate">{item.label}</span>
                          <span className="mt-0.5 block truncate text-[10px] font-normal text-slate-500">
                            {item.description}
                          </span>
                        </span>
                      </>
                    )}
                  </NavLink>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="border-t border-white/10 px-5 py-4">
        <p className="text-[10px] leading-4 text-slate-500">
          Observability only. Runtime mode changes and policy edits are not
          available from this dashboard.
        </p>
      </div>
    </div>
  );
}

export function Sidebar({ mobileOpen, onClose }: SidebarProps) {
  return (
    <>
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-72 border-r border-white/5 lg:block">
        <SidebarContent />
      </aside>

      <div
        className={cn(
          "fixed inset-0 z-50 lg:hidden",
          mobileOpen ? "pointer-events-auto" : "pointer-events-none",
        )}
        aria-hidden={!mobileOpen}
      >
        <button
          type="button"
          aria-label="Close navigation"
          onClick={onClose}
          className={cn(
            "absolute inset-0 bg-slate-950/50 transition-opacity",
            mobileOpen ? "opacity-100" : "opacity-0",
          )}
          tabIndex={mobileOpen ? 0 : -1}
        />
        <div
          className={cn(
            "absolute inset-y-0 left-0 w-[19rem] max-w-[85vw] shadow-2xl transition-transform duration-200",
            mobileOpen ? "translate-x-0" : "-translate-x-full",
          )}
        >
          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation"
            className="absolute right-3 top-3 z-10 flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-white/10 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
          >
            <X aria-hidden="true" className="h-4 w-4" />
          </button>
          <SidebarContent onNavigate={onClose} />
        </div>
      </div>
    </>
  );
}
