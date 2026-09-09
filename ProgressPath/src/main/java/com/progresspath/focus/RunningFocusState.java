package com.progresspath.focus;

public final class RunningFocusState extends AbstractFocusState {
    @Override
    public void pause(FocusTimer timer) {
        timer.stopCurrentInterval();
        timer.changeState(new PausedFocusState());
    }

    @Override
    public void complete(FocusTimer timer) {
        timer.stopCurrentInterval();
        timer.finishTiming();
        timer.changeState(new CompletedFocusState());
    }

    @Override
    public void cancel(FocusTimer timer) {
        timer.stopCurrentInterval();
        timer.finishTiming();
        timer.changeState(new CancelledFocusState());
    }

    @Override
    public String getName() {
        return "Running";
    }
}
