import { formatMode } from "../../lib/format";
import { Badge } from "./Badge";

export function ModeBadge({ mode }: { mode: string | null | undefined }) {
  const isAdaptive = mode === "ADAPTIVE";
  return (
    <Badge tone={isAdaptive ? "purple" : "info"} dot>
      {formatMode(mode)} mode
    </Badge>
  );
}
