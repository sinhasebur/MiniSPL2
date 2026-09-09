package com.progresspath.progress;

import com.progresspath.model.Chapter;

import java.util.List;

/** Strategy pattern: each implementation is one progress-calculation policy. */
public interface ProgressCalculationStrategy {
    double calculate(List<Chapter> chapters);
}
