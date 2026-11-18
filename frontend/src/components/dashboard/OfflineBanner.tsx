import { WifiOff } from "lucide-react";

import { apiErrorMessage } from "../../api/client";

export function OfflineBanner({ error }: { error: Error | null }) {
  return (
    <div
      role="alert"
      className="flex gap-3 border-b border-rose-200 bg-rose-50 px-4 py-2.5 text-xs text-rose-900 sm:px-6 lg:px-8"
    >
      <WifiOff aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
      <p>
        <span className="font-semibold">Backend offline.</span>{" "}
        {error
          ? apiErrorMessage(error)
          : "The health endpoint could not be reached."}{" "}
        Cached views remain visible and polling will retry automatically.
      </p>
    </div>
  );
}
