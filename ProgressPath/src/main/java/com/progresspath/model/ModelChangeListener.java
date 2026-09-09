package com.progresspath.model;

/** Observer pattern: screens subscribe to model changes through this small interface. */
public interface ModelChangeListener {
    void onModelChanged(ModelChangeType changeType);
}
