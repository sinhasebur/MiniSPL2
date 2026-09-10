package com.progresspath.ai;

import java.io.IOException;

/**
 * Provider boundary for the assistant feature.
 *
 * Keeping the controller dependent on this interface makes the AI provider
 * replaceable without changing the JavaFX view or the ProgressPath model.
 */
public interface AssistantProvider {
    String ask(String question, String studyContext) throws IOException, InterruptedException;
}
