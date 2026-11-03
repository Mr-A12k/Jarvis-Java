package com.jarvis.vision;

import com.jarvis.chat.ChatRequest;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VisionAnalysisRequest(
        String prompt,
        @Size(max = 15_000_000) String imageBase64,
        List<ChatRequest.Turn> history,
        Boolean autoCapture
) {
    public VisionAnalysisRequest {
        prompt = (prompt == null || prompt.isBlank()) ? "Look at my screen and help me debug" : prompt.trim();
        history = history == null ? List.of() : List.copyOf(history);
        autoCapture = autoCapture != null && autoCapture;
    }
}
