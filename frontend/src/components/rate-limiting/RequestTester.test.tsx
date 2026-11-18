import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "../../api/client";
import type { DemoResponse } from "../../api/types";
import { RequestTester } from "./RequestTester";

function demoResponse(overrides: Partial<DemoResponse> = {}): DemoResponse {
  return {
    status: 200,
    statusText: "OK",
    ok: true,
    body: '{"message":"ok"}',
    contentType: "application/json",
    latencyMs: 12,
    rateLimitLimit: "5",
    rateLimitRemaining: "4",
    retryAfter: null,
    ...overrides,
  };
}

function fillForm(clientId: string, requestCount: string) {
  fireEvent.change(screen.getByLabelText("Client ID"), {
    target: { value: clientId },
  });
  fireEvent.change(screen.getByLabelText("Request count"), {
    target: { value: requestCount },
  });
}

describe("RequestTester", () => {
  it("rejects invalid inputs before sending requests", () => {
    const sendRequest = vi.fn();
    render(
      <RequestTester currentLimit={5} sendRequest={sendRequest} />,
    );

    fillForm("invalid client id", "0");
    fireEvent.click(screen.getByRole("button", { name: "Run test" }));

    expect(screen.getByText("Check the test inputs")).toBeInTheDocument();
    expect(
      screen.getByText(/Client ID must be 1-64 characters/i),
    ).toBeInTheDocument();
    expect(sendRequest).not.toHaveBeenCalled();
  });

  it("separates actual 200 and 429 responses and shows headers and bodies", async () => {
    const sendRequest = vi
      .fn()
      .mockResolvedValueOnce(
        demoResponse({
          body: '{"message":"first"}',
          rateLimitRemaining: "4",
        }),
      )
      .mockResolvedValueOnce(
        demoResponse({
          body: '{"message":"second"}',
          rateLimitRemaining: "3",
        }),
      )
      .mockResolvedValueOnce(
        demoResponse({
          status: 429,
          statusText: "Too Many Requests",
          ok: false,
          body: '{"message":"slow down"}',
          rateLimitRemaining: "0",
          retryAfter: "1",
          latencyMs: 9,
        }),
      );

    render(<RequestTester currentLimit={5} sendRequest={sendRequest} />);

    fillForm("phase1-test", "3");
    fireEvent.click(screen.getByRole("button", { name: "Run test" }));

    await waitFor(() => {
      expect(screen.getByTestId("request-tester-allowed")).toHaveTextContent(
        "2",
      );
    });
    expect(
      screen.getByTestId("request-tester-rejected"),
    ).toHaveTextContent("1");
    expect(screen.getByTestId("request-tester-failed")).toHaveTextContent("0");
    expect(screen.getByTestId("request-tester-other")).toHaveTextContent("0");
    expect(sendRequest).toHaveBeenCalledTimes(3);
    expect(sendRequest.mock.calls[0]?.[0]).toBe("phase1-test");

    const rejectedRow = screen.getByTestId("request-result-3");
    expect(
      within(rejectedRow).getByText("HTTP 429 Rejected"),
    ).toBeInTheDocument();
    expect(within(rejectedRow).getByText("9 ms")).toBeInTheDocument();
    expect(within(rejectedRow).getByText("0")).toBeInTheDocument();
    expect(within(rejectedRow).getByText("1")).toBeInTheDocument();

    fireEvent.click(within(rejectedRow).getByText("View body"));
    expect(
      within(rejectedRow).getByText(/"message": "slow down"/),
    ).toBeInTheDocument();
  });

  it("shows loading progress and reports network failures", async () => {
    let resolveRequest: (value: DemoResponse) => void = () => {};
    const sendRequest = vi.fn(
      () =>
        new Promise<DemoResponse>((resolve) => {
          resolveRequest = resolve;
        }),
    );

    render(<RequestTester currentLimit={2} sendRequest={sendRequest} />);

    fillForm("phase1-loading", "1");
    fireEvent.click(screen.getByRole("button", { name: "Run test" }));

    expect(
      await screen.findByText(/Sending request 1 of 1/i),
    ).toBeInTheDocument();

    resolveRequest(demoResponse());

    await waitFor(() => {
      expect(screen.queryByText(/Sending request/i)).not.toBeInTheDocument();
    });
    expect(screen.getByText("HTTP 200 Allowed")).toBeInTheDocument();
  });

  it("reports a network failure without fabricating an HTTP response", async () => {
    const sendRequest = vi
      .fn()
      .mockRejectedValue(new ApiError("Network request failed.", "/api/demo"));

    render(<RequestTester currentLimit={2} sendRequest={sendRequest} />);

    fillForm("phase1-offline", "1");
    fireEvent.click(screen.getByRole("button", { name: "Run test" }));

    await waitFor(() => {
      expect(screen.getByTestId("request-tester-failed")).toHaveTextContent(
        "1",
      );
    });
    expect(screen.getByText("Network failure")).toBeInTheDocument();
    expect(
      screen.getByText(/The backend could not be reached/i),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("request-tester-allowed"),
    ).toHaveTextContent("0");
    expect(
      screen.getByTestId("request-tester-rejected"),
    ).toHaveTextContent("0");
  });
});
