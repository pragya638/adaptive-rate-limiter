package com.example.ratelimiter.monitoring;

public class EwmaCalculator {
    private final double alpha;
    private Double smoothed;
    private boolean initialized;

    public EwmaCalculator(double alpha) {
        if (alpha <= 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in (0, 1]");
        }
        this.alpha = alpha;
        this.smoothed = null;
        this.initialized = false;
    }

    public synchronized void update(double observedRatePerSecond) {
        double x = observedRatePerSecond;
        if (Double.isNaN(x) || Double.isInfinite(x) || x < 0.0) {
            x = 0.0;
        }
        if (!initialized) {
            smoothed = Double.valueOf(x);
            initialized = true;
            return;
        }
        double sPrev = smoothed.doubleValue();
        double sNew = alpha * x + (1.0 - alpha) * sPrev;
        if (Double.isNaN(sNew) || Double.isInfinite(sNew)) {
            sNew = sPrev;
        }
        smoothed = Double.valueOf(sNew);
    }

    public double getSmoothed() {
        if (smoothed == null) {
            return 0.0;
        }
        return smoothed.doubleValue();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public double getAlpha() {
        return alpha;
    }
}
