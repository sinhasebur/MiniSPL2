package com.progresspath.progress;

import com.progresspath.model.Chapter;

import java.util.List;

public final class WeightedChapterProgressStrategy implements ProgressCalculationStrategy {
    @Override
    public double calculate(List<Chapter> chapters) {
        double totalWeight = 0.0;
        double weightedProgress = 0.0;
        for (Chapter chapter : chapters) {
            totalWeight += chapter.weight();
            weightedProgress += chapter.progress() * chapter.weight();
        }
        if (totalWeight == 0.0) {
            return 0.0;
        }
        return weightedProgress / totalWeight;
    }
}
