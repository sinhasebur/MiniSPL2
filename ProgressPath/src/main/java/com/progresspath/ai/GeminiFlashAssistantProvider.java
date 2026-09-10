package com.progresspath.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Gemini Flash implementation of the assistant provider.
 *
 * The provider is deliberately independent of JavaFX so it can be tested and
 * replaced without coupling network work to the View or Controller.
 */
public final class GeminiFlashAssistantProvider implements AssistantProvider {
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String SYSTEM_INSTRUCTION =
            "You are the ProgressPath study assistant. Use only the supplied study context. "
                    + "Do not invent courses, dates, progress values, or assignments. "
                    + "Give practical, concise study guidance. If the context is insufficient, "
                    + "say what is missing. Do not claim to change the user's data.";

    private final GeminiConfiguration configuration;
    private final HttpClient httpClient;

    public GeminiFlashAssistantProvider() {
        this(GeminiConfiguration.fromEnvironment());
    }

    GeminiFlashAssistantProvider(GeminiConfiguration configuration) {
        this.configuration = configuration;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(configuration.timeout())
                .build();
    }

    @Override
    public String ask(String question, String studyContext) throws IOException, InterruptedException {
        if (!configuration.isConfigured()) {
            throw new IllegalStateException(
                    "Gemini is not configured. Add GOOGLE_API_KEY or GEMINI_API_KEY to .env or your environment.");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Enter a question for the assistant.");
        }
        String context = studyContext == null || studyContext.isBlank()
                ? "No study plan is selected."
                : studyContext;
        String requestBody = GeminiJson.request(SYSTEM_INSTRUCTION, context, question.trim());
        String endpoint = ENDPOINT + configuration.model() + ":generateContent";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(configuration.timeout())
                .header("Content-Type", "application/json")
                // Header authentication keeps the key out of the request URL.
                .header("x-goog-api-key", configuration.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gemini request failed (" + response.statusCode() + "): "
                    + GeminiResponseParser.errorMessage(response.body()));
        }
        String answer = GeminiResponseParser.firstText(response.body());
        if (answer.isBlank()) {
            throw new IOException("Gemini returned no answer. Try asking a shorter question.");
        }
        return answer.trim();
    }

    public String modelName() {
        return configuration.model();
    }

    public boolean isConfigured() {
        return configuration.isConfigured();
    }
}
