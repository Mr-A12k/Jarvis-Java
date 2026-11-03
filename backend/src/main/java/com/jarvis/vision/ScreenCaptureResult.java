package com.jarvis.vision;

public record ScreenCaptureResult(
        String imageBase64,
        String mimeType,
        int width,
        int height,
        long sizeBytes,
        String source,
        long timestamp
) {
    public boolean isAvailable() {
        return imageBase64 != null && !imageBase64.isBlank() && !"unavailable".equalsIgnoreCase(source);
    }
}
