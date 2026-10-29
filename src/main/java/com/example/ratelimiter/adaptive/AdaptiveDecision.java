package com.example.ratelimiter.adaptive;

import java.time.Instant;

public class AdaptiveDecision {
    private final DecisionAction action;
    private final int currentLimit;
    private final int proposedLimit;
    private final String reasonCode;
    private final String explanation;
    private final Instant evaluatedAt;

    public AdaptiveDecision(DecisionAction action, int currentLimit, int proposedLimit,
            String reasonCode, String explanation, Instant evaluatedAt) {
        this.action = action;
        this.currentLimit = currentLimit;
        this.proposedLimit = proposedLimit;
        this.reasonCode = reasonCode;
        this.explanation = explanation;
        this.evaluatedAt = evaluatedAt;
    }

    public DecisionAction getAction() {
        return action;
    }

    public int getCurrentLimit() {
        return currentLimit;
    }

    public int getProposedLimit() {
        return proposedLimit;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getExplanation() {
        return explanation;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
