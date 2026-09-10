package com.progresspath.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiResponseParserTest {
    @Test
    void extractsAndDecodesTheFirstResponseText() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Study\\nchapter 1 \\\"first\\\".\"}]}}]}";

        assertEquals("Study\nchapter 1 \"first\".", GeminiResponseParser.firstText(json));
    }

    @Test
    void returnsAUsefulFallbackWhenAnErrorHasNoMessage() {
        assertTrue(GeminiResponseParser.errorMessage("{\"error\":{\"code\":500}}")
                .contains("Gemini service"));
    }
}
