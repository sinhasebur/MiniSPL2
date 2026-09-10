package com.progresspath.ai;

/**
 * Small JSON writer for the Groq request body. The project only needs three
 * string values, so keeping this local avoids another runtime dependency.
 */
final class GroqJson {
    private GroqJson() {
    }

    static String request(String model, String systemInstruction, String studyContext, String question) {
        String prompt = "Study context:\n" + studyContext + "\n\nStudent question:\n" + question;
        return "{"
                + "\"model\":" + quote(model) + ","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + quote(systemInstruction) + "},"
                + "{\"role\":\"user\",\"content\":" + quote(prompt) + "}"
                + "],"
                + "\"temperature\":0.4,"
                + "\"max_tokens\":512"
                + "}";
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder();
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                    break;
            }
        }
        result.append('"');
        return result.toString();
    }
}
