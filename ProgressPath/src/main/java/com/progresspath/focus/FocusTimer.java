package com.progresspath.focus;

import java.time.Duration;
import java.time.LocalDateTime;

/** State pattern context: public actions are delegated to the current state object. */
public final class FocusTimer {
    private FocusSessionState state = new ReadyFocusState();
    private LocalDateTime startedAt;
    private LocalDateTime lastResumedAt;
    private LocalDateTime endedAt;
    private long accumulatedSeconds;

    public void start() {
        state.start(this);
    }

    public void pause() {
        state.pause(this);
    }

    public void resume() {
        state.resume(this);
    }

    public void complete() {
        state.complete(this);
    }

    public void cancel() {
        state.cancel(this);
    }

    public String getStateName() {
        return state.getName();
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public long getElapsedSeconds() {
        if (state instanceof RunningFocusState) {
            return accumulatedSeconds + secondsBetween(lastResumedAt, LocalDateTime.now());
        }
        return accumulatedSeconds;
    }

    public boolean isCompleted() {
        return state instanceof CompletedFocusState;
    }

    public boolean isCancelled() {
        return state instanceof CancelledFocusState;
    }

    void beginTiming() {
        startedAt = LocalDateTime.now();
        lastResumedAt = startedAt;
        endedAt = null;
        accumulatedSeconds = 0;
    }

    void resumeTiming() {
        lastResumedAt = LocalDateTime.now();
    }

    void stopCurrentInterval() {
        accumulatedSeconds += secondsBetween(lastResumedAt, LocalDateTime.now());
    }

    void finishTiming() {
        endedAt = LocalDateTime.now();
    }

    void changeState(FocusSessionState newState) {
        state = newState;
    }

    private long secondsBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, end).getSeconds());
    }
}
