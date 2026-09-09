package com.progresspath.focus;

public final class ReadyFocusState extends AbstractFocusState {
    @Override
    public void start(FocusTimer timer) {
        timer.beginTiming();
        timer.changeState(new RunningFocusState());
    }

    @Override
    public String getName() {
        return "Ready";
    }
}
