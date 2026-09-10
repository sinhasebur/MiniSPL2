package com.progresspath.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Groq implementation of the assistant provider.
 *
 * The provider is deliberately independent of JavaFX so it can be tested and
 * replaced without coupling network work to the View or Controller.
 */
public final class GroqAssistantProvider implements AssistantProvider {
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String SYSTEM_INSTRUCTION =
            "You are the ProgressPath study assistant. Use only the supplied study context. "
                    + "Do not invent courses, dates, progress values, or assignments. "
                    + "Give practical, concise study guidance. If the context is insufficient, "
                    + "say what is missing. Do not claim to change the user's data. "
                    + "IMPORTANT: Output ONLY plain text. Do NOT use Markdown formatting (no asterisks, no tables, no HTML tags).";

    private final GroqConfiguration configuration;
    private final HttpClient httpClient;

    public GroqAssistantProvider() {
        this(GroqConfiguration.fromEnvironment());
    }

    GroqAssistantProvider(GroqConfiguration configuration) {
        this.configuration = configuration;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(configuration.timeout())
                .build();
    }

    @Override
    public String ask(String question, String studyContext) throws IOException, InterruptedException {
        if (!configuration.isConfigured()) {
            throw new IllegalStateException(
                    "Groq is not configured. Add GROQ_API_KEY to .env or your environment.");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Enter a question for the assistant.");
        }
        String context = studyContext == null || studyContext.isBlank()
                ? "No study plan is selected."
                : studyContext;
        
        String requestBody = GroqJson.request(configuration.model(), SYSTEM_INSTRUCTION, context, question.trim());
        
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(configuration.timeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + configuration.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IOException("Groq rejected this API key (" + response.statusCode()
                        + "). Check your API key at console.groq.com.");
            }
            throw new IOException("Groq request failed (" + response.statusCode() + "): "
                    + GroqResponseParser.errorMessage(response.body()));
        }
        String answer = GroqResponseParser.firstContent(response.body());
        if (answer.isBlank()) {
            throw new IOException("Groq returned no answer. Try asking a shorter question.");
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
