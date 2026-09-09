package com.progresspath.focus;

public final class PausedFocusState extends AbstractFocusState {
    @Override
    public void resume(FocusTimer timer) {
        timer.resumeTiming();
        timer.changeState(new RunningFocusState());
    }

    @Override
    public void complete(FocusTimer timer) {
        timer.finishTiming();
        timer.changeState(new CompletedFocusState());
    }

    @Override
    public void cancel(FocusTimer timer) {
        timer.finishTiming();
        timer.changeState(new CancelledFocusState());
    }

    @Override
    public String getName() {
        return "Paused";
    }
}
