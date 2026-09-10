package com.progresspath.ai;

/**
 * Extracts the first string value for a JSON key and decodes JSON escapes.
 * Gemini responses are intentionally handled narrowly so this demo stays
 * dependency-free; the parser does not attempt to be a general JSON parser.
 */
final class GeminiResponseParser {
    private GeminiResponseParser() {
    }

    static String firstText(String json) {
        return firstString(json, "text");
    }

    static String errorMessage(String json) {
        String message = firstString(json, "message");
        return message.isBlank() ? "The Gemini service returned an error." : message;
    }

    private static String firstString(String json, String key) {
        String marker = "\"" + key + "\"";
        int searchFrom = 0;
        while (searchFrom < json.length()) {
            int keyStart = json.indexOf(marker, searchFrom);
            if (keyStart < 0) {
                return "";
            }
            int colon = json.indexOf(':', keyStart + marker.length());
            if (colon < 0) {
                return "";
            }
            int valueStart = skipWhitespace(json, colon + 1);
            if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                return readString(json, valueStart + 1);
            }
            searchFrom = keyStart + marker.length();
        }
        return "";
    }

    private static int skipWhitespace(String value, int index) {
        int result = index;
        while (result < value.length() && Character.isWhitespace(value.charAt(result))) {
            result++;
        }
        return result;
    }

    private static String readString(String json, int start) {
        StringBuilder result = new StringBuilder();
        for (int index = start; index < json.length(); index++) {
            char character = json.charAt(index);
            if (character == '"') {
                return result.toString();
            }
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (index + 1 >= json.length()) {
                return result.toString();
            }
            char escaped = json.charAt(++index);
            switch (escaped) {
                case '"':
                    result.append('"');
                    break;
                case '\\':
                    result.append('\\');
                    break;
                case '/':
                    result.append('/');
                    break;
                case 'b':
                    result.append('\b');
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'u':
                    if (index + 4 < json.length()) {
                        result.append((char) Integer.parseInt(json.substring(index + 1, index + 5), 16));
                        index += 4;
                    }
                    break;
                default:
                    result.append(escaped);
                    break;
            }
        }
        return result.toString();
    }
}
