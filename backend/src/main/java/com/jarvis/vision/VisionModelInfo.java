package com.jarvis.vision;

import java.util.List;

public record VisionModelInfo(
        boolean visionModelAvailable,
        String activeModel,
        List<String> installedVisionModels,
        String recommendedModel,
        boolean hostCaptureSupported
) {}
