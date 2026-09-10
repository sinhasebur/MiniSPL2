package com.progresspath.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqResponseParserTest {

    @Test
    void extractsContentFromGroqResponse() {
        String json = "{\"id\":\"chatcmpl-123\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Study\\nchapter 1 \\\"first\\\".\"}}]}";
        assertEquals("Study\nchapter 1 \"first\".", GroqResponseParser.firstContent(json));
    }

    @Test
    void extractsErrorMessage() {
        assertTrue(GroqResponseParser.errorMessage("{\"error\":{\"message\":\"Invalid API key\"}}")
                .contains("Invalid API key"));
    }
}
