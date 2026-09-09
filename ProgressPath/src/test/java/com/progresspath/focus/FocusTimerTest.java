package com.progresspath.focus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FocusTimerTest {
    @Test
    void statePatternControlsFocusLifecycle() {
        FocusTimer timer = new FocusTimer();
        assertEquals("Ready", timer.getStateName());

        timer.start();
        assertEquals("Running", timer.getStateName());
        timer.pause();
        assertEquals("Paused", timer.getStateName());
        timer.resume();
        assertEquals("Running", timer.getStateName());
        timer.complete();
        assertEquals("Completed", timer.getStateName());
    }
}
