package com.progresspath.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads local Gemini settings without adding a dotenv dependency.
 *
 * Environment variables and JVM properties take precedence over .env values,
 * which makes deployment configuration explicit while keeping local setup easy.
 */
public final class GeminiConfiguration {
    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    public static final int DEFAULT_TIMEOUT_SECONDS = 45;

    private final String apiKey;
    private final String model;
    private final Duration timeout;

    private GeminiConfiguration(String apiKey, String model, Duration timeout) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    public static GeminiConfiguration fromEnvironment() {
        Map<String, String> dotEnv = readDotEnv();
        String apiKey = firstValue(
                System.getenv("GOOGLE_API_KEY"),
                System.getenv("GEMINI_API_KEY"),
                System.getProperty("google.api.key"),
                System.getProperty("gemini.api.key"),
                dotEnv.get("GOOGLE_API_KEY"),
                dotEnv.get("GEMINI_API_KEY")
        );
        String model = firstValue(
                System.getenv("GEMINI_MODEL"),
                System.getProperty("gemini.model"),
                dotEnv.get("GEMINI_MODEL"),
                DEFAULT_MODEL
        );
        String timeoutValue = firstValue(
                System.getenv("GEMINI_TIMEOUT_SECONDS"),
                System.getProperty("gemini.timeout.seconds"),
                dotEnv.get("GEMINI_TIMEOUT_SECONDS"),
                String.valueOf(DEFAULT_TIMEOUT_SECONDS)
        );
        return new GeminiConfiguration(apiKey, model, parseTimeout(timeoutValue));
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public Duration timeout() {
        return timeout;
    }

    private static Duration parseTimeout(String value) {
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 5 || seconds > 300) {
                return Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException exception) {
            return Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
        }
    }

    private static String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Map<String, String> readDotEnv() {
        Map<String, String> values = new HashMap<>();
        Path[] candidates = new Path[]{
                Paths.get(".env"),
                Paths.get("ProgressPath", ".env"),
                Paths.get("..", ".env")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                loadDotEnvFile(candidate, values);
                break;
            }
        }
        return values;
    }

    private static void loadDotEnvFile(Path file, Map<String, String> values) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                values.put(name, removeOptionalQuotes(value));
            }
        } catch (IOException ignored) {
            // A missing or unreadable local .env is reported later as an
            // unconfigured assistant rather than preventing the application
            // from opening.
        }
    }

    private static String removeOptionalQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
