package com.progresspath.progress;

import com.progresspath.model.Chapter;

import java.util.List;

public final class EqualChapterProgressStrategy implements ProgressCalculationStrategy {
    @Override
    public double calculate(List<Chapter> chapters) {
        if (chapters.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Chapter chapter : chapters) {
            total += chapter.progress();
        }
        return total / chapters.size();
    }
}
