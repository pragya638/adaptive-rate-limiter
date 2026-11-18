import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "../../api/client";
import { OfflineBanner } from "../dashboard/OfflineBanner";
import { StaleDataBanner } from "../dashboard/StaleDataBanner";
import { EmptyState } from "./EmptyState";
import { ErrorState } from "./ErrorState";
import { LoadingState } from "./LoadingState";
import { MetricCard } from "./MetricCard";
import { Activity } from "lucide-react";

describe("dashboard state components", () => {
  it("renders a loading state with an accessible busy region", () => {
    render(<LoadingState label="Loading test data" />);

    expect(screen.getByText("Loading test data")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveAttribute("aria-busy", "true");
  });

  it("renders an empty state with a clear explanation", () => {
    render(
      <EmptyState
        title="No history"
        description="This appears after the first completed window."
      />,
    );

    expect(screen.getByText("No history")).toBeInTheDocument();
    expect(
      screen.getByText("This appears after the first completed window."),
    ).toBeInTheDocument();
  });

  it("renders an error state and invokes retry", () => {
    const onRetry = vi.fn();
    render(
      <ErrorState
        title="Backend unavailable"
        message="Could not reach the service."
        onRetry={onRetry}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Retry now" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("renders the backend-offline banner", () => {
    render(
      <OfflineBanner
        error={new ApiError("Network request failed.", "/api/health")}
      />,
    );

    expect(screen.getByText("Backend offline.")).toBeInTheDocument();
    expect(
      screen.getByText(/Cached views remain visible/i),
    ).toBeInTheDocument();
  });

  it("marks stale cached data after a failed refresh", () => {
    render(
      <StaleDataBanner
        dataUpdatedAt={Date.now() - 60_000}
        isError
      />,
    );

    expect(screen.getByText(/showing stale data/i)).toBeInTheDocument();
  });

  it("renders metric values and an unavailable treatment", () => {
    const { rerender } = render(
      <MetricCard
        label="Current rate"
        value="4 req/s"
        helper="Last window"
        icon={Activity}
      />,
    );

    expect(screen.getByText("Current rate")).toBeInTheDocument();
    expect(screen.getByText("4 req/s")).toBeInTheDocument();

    rerender(
      <MetricCard
        label="Current rate"
        value="—"
        helper="Not available"
        icon={Activity}
        unavailable
      />,
    );
    expect(screen.getByText("—")).toHaveClass("text-slate-400");
  });
});
