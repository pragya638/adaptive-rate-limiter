import { describe, expect, it } from "vitest";

import { decisionActionTone } from "./decision";

describe("decision action tone", () => {
  it("maps the real backend action values correctly", () => {
    expect(decisionActionTone("INCREASE_LIMIT")).toBe("success");
    expect(decisionActionTone("DECREASE_LIMIT")).toBe("danger");
    expect(decisionActionTone("KEEP_LIMIT")).toBe("neutral");
    expect(decisionActionTone(null)).toBe("neutral");
  });
});
