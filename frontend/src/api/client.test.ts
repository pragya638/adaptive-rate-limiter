import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, api } from "./client";

function response(
  status: number,
  body: string,
  headers: Record<string, string>,
): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 429 ? "Too Many Requests" : "OK",
    headers: {
      get(name: string) {
        return headers[name.toLowerCase()] ?? null;
      },
    },
    text: async () => body,
  } as unknown as Response;
}

describe("demo request client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns a successful response with limiter headers", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      response(
        200,
        '{"message":"ok"}',
        {
          "content-type": "application/json",
          "x-ratelimit-limit": "5",
          "x-ratelimit-remaining": "4",
        },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await api.demoRequest("test-client");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/demo",
      expect.objectContaining({
        headers: {
          Accept: "application/json",
          "X-Client-Id": "test-client",
        },
      }),
    );
    expect(result.status).toBe(200);
    expect(result.ok).toBe(true);
    expect(result.body).toBe('{"message":"ok"}');
    expect(result.rateLimitLimit).toBe("5");
    expect(result.rateLimitRemaining).toBe("4");
    expect(result.retryAfter).toBeNull();
    expect(result.latencyMs).toBeGreaterThanOrEqual(0);
  });

  it("returns a 429 response without throwing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        response(
          429,
          '{"status":429,"message":"Rate limit exceeded."}',
          {
            "content-type": "application/json",
            "x-ratelimit-limit": "5",
            "x-ratelimit-remaining": "0",
            "retry-after": "1",
          },
        ),
      ),
    );

    const result = await api.demoRequest("test-client");

    expect(result.status).toBe(429);
    expect(result.ok).toBe(false);
    expect(result.statusText).toBe("Too Many Requests");
    expect(result.rateLimitLimit).toBe("5");
    expect(result.rateLimitRemaining).toBe("0");
    expect(result.retryAfter).toBe("1");
  });

  it("converts network failures into an ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));

    await expect(api.demoRequest("test-client")).rejects.toBeInstanceOf(
      ApiError,
    );
  });
});
