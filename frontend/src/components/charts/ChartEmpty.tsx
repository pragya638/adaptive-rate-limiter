import { BarChart3 } from "lucide-react";

import { EmptyState } from "../ui/EmptyState";

export function ChartEmpty({
  title = "No retained history yet",
  description = "This chart fills after the backend completes monitoring windows and the dashboard refreshes its retained history.",
}: {
  title?: string;
  description?: string;
}) {
  return (
    <EmptyState
      compact
      icon={<BarChart3 aria-hidden="true" className="h-5 w-5" />}
      title={title}
      description={description}
    />
  );
}
