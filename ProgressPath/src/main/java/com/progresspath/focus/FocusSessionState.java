package com.progresspath.focus;

/** State pattern: each object defines the actions valid in one timer state. */
public interface FocusSessionState {
    void start(FocusTimer timer);

    void pause(FocusTimer timer);

    void resume(FocusTimer timer);

    void complete(FocusTimer timer);

    void cancel(FocusTimer timer);

    String getName();
}
