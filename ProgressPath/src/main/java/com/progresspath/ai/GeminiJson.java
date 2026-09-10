package com.progresspath.ai;

/**
 * Small JSON writer for the Gemini request body. The project only needs three
 * string values, so keeping this local avoids another runtime dependency.
 */
final class GeminiJson {
    private GeminiJson() {
    }

    static String request(String systemInstruction, String studyContext, String question) {
        String prompt = "Study context:\n" + studyContext + "\n\nStudent question:\n" + question;
        return "{"
                + "\"system_instruction\":{\"parts\":[{\"text\":" + quote(systemInstruction) + "}]},"
                + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":" + quote(prompt) + "}]}],"
                + "\"generationConfig\":{\"temperature\":0.4,\"maxOutputTokens\":512}"
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
