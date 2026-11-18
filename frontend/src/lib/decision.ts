import type { DecisionAction } from "../api/types";

export type DecisionTone = "success" | "danger" | "neutral";

export function decisionActionTone(
  action: DecisionAction | string | null | undefined,
): DecisionTone {
  if (action === "INCREASE_LIMIT") {
    return "success";
  }
  if (action === "DECREASE_LIMIT") {
    return "danger";
  }
  return "neutral";
}
