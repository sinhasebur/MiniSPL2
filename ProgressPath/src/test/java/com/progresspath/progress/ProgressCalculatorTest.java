package com.progresspath.progress;

import com.progresspath.model.Chapter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressCalculatorTest {
    private final List<Chapter> chapters = List.of(
            new Chapter(1, 10, "Foundations", 40, 50, LocalDate.of(2026, 9, 15)),
            new Chapter(2, 10, "Applications", 60, 100, LocalDate.of(2026, 9, 30))
    );

    @Test
    void calculatesWeightedChapterProgress() {
        ProgressCalculator calculator = new ProgressCalculator(new WeightedChapterProgressStrategy());

        assertEquals(80.0, calculator.calculate(chapters), 0.001);
    }

    @Test
    void canSwitchToEqualChapterProgress() {
        ProgressCalculator calculator = new ProgressCalculator(new WeightedChapterProgressStrategy());
        calculator.setStrategy(new EqualChapterProgressStrategy());

        assertEquals(75.0, calculator.calculate(chapters), 0.001);
    }
}
