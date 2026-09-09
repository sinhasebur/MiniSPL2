package com.progresspath.progress;

import com.progresspath.model.Chapter;

import java.util.List;
import java.util.Objects;

/** Strategy pattern context: the calculation policy can be changed without changing callers. */
public final class ProgressCalculator {
    private ProgressCalculationStrategy strategy;

    public ProgressCalculator(ProgressCalculationStrategy strategy) {
        setStrategy(strategy);
    }

    public void setStrategy(ProgressCalculationStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public double calculate(List<Chapter> chapters) {
        return strategy.calculate(List.copyOf(chapters));
    }
}
