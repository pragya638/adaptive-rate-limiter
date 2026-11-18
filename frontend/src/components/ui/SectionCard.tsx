import type { ReactNode } from "react";

import { cn } from "../../lib/cn";

interface SectionCardProps {
  title?: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  noBodyPadding?: boolean;
}

export function SectionCard({
  title,
  description,
  actions,
  children,
  className,
  bodyClassName,
  noBodyPadding = false,
}: SectionCardProps) {
  return (
    <section className={cn("card", className)}>
      {title || description || actions ? (
        <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            {title ? (
              <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
            ) : null}
            {description ? (
              <p className="mt-0.5 text-xs leading-5 text-slate-500">
                {description}
              </p>
            ) : null}
          </div>
          {actions ? (
            <div className="flex shrink-0 items-center gap-2">{actions}</div>
          ) : null}
        </div>
      ) : null}
      <div
        className={cn(
          noBodyPadding ? "" : "card-body",
          bodyClassName,
        )}
      >
        {children}
      </div>
    </section>
  );
}
