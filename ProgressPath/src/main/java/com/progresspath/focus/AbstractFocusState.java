package com.progresspath.focus;

abstract class AbstractFocusState implements FocusSessionState {
    @Override
    public void start(FocusTimer timer) {
        invalid("start");
    }

    @Override
    public void pause(FocusTimer timer) {
        invalid("pause");
    }

    @Override
    public void resume(FocusTimer timer) {
        invalid("resume");
    }

    @Override
    public void complete(FocusTimer timer) {
        invalid("complete");
    }

    @Override
    public void cancel(FocusTimer timer) {
        invalid("cancel");
    }

    private void invalid(String action) {
        throw new IllegalStateException("Cannot " + action + " a session that is " + getName().toLowerCase() + ".");
    }
}
