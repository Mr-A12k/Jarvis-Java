package com.jarvis.files;

import com.jarvis.common.ApiException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PptxTextTest {

    static byte[] createPptx(Map<String, String> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void extractsMultiSlidePresentations() throws Exception {
        String slide1 = "<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                + "<p:cSld><p:spTree><p:sp><p:txBody>"
                + "<a:p><a:r><a:t>Q3 Project Overview</a:t></a:r></a:p>"
                + "</p:txBody></p:sp></p:spTree></p:cSld></p:sld>";

        String slide2 = "<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                + "<p:cSld><p:spTree><p:sp><p:txBody>"
                + "<a:p><a:r><a:t>Key Deliverables: J.A.R.V.I.S. Architecture</a:t></a:r></a:p>"
                + "</p:txBody></p:sp></p:spTree></p:cSld></p:sld>";

        byte[] pptx = createPptx(Map.of(
                "ppt/slides/slide1.xml", slide1,
                "ppt/slides/slide2.xml", slide2
        ));

        String extracted = PptxText.extract(pptx);
        assertThat(extracted).contains("--- Slide 1 ---");
        assertThat(extracted).contains("Q3 Project Overview");
        assertThat(extracted).contains("--- Slide 2 ---");
        assertThat(extracted).contains("Key Deliverables: J.A.R.V.I.S. Architecture");
    }

    @Test
    void rejectsMissingOrMalformedSlides() throws Exception {
        assertThatThrownBy(() -> PptxText.extract(createPptx(Map.of("ppt/other.xml", "data"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no slide data");

        assertThatThrownBy(() -> PptxText.extract(createPptx(Map.of("ppt/slides/slide1.xml", "<malformed"))))
                .isInstanceOf(ApiException.class);
    }
}
