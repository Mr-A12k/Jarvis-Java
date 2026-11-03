package com.jarvis.files;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import com.jarvis.common.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PdfTextTest {
    static byte[] pdf(String text, int pages, boolean encrypted) throws IOException {
        try (var doc = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i=0;i<pages;i++) {
                var page = new PDPage(); doc.addPage(page);
                if (!text.isEmpty()) try (var stream = new PDPageContentStream(doc,page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    stream.newLineAtOffset(50,700); stream.showText(text); stream.endText();
                }
            }
            if (encrypted) doc.protect(new StandardProtectionPolicy("owner", "reader", new AccessPermission()));
            doc.save(output); return output.toByteArray();
        }
    }
    @Test void extractsTextAcrossPages() throws Exception {
        String extracted = PdfText.extract(pdf("Project budget is 400.",2,false));
        assertThat(extracted).contains("--- Page 1 ---", "--- Page 2 ---", "Project budget is 400.");
    }
    @Test void rejectsEncryptedMalformedAndTextlessDocuments() {
        assertThatThrownBy(() -> PdfText.extract(pdf("private",1,true))).isInstanceOf(ApiException.class).hasMessageContaining("Password");
        assertThatThrownBy(() -> PdfText.extract("not a pdf".getBytes())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PdfText.extract(pdf("",1,false))).isInstanceOf(ApiException.class).hasMessageContaining("OCR");
    }
    @Test void rejectsPageFileAndExtractedTextLimits() {
        assertThatThrownBy(() -> PdfText.extract(pdf("",51,false))).isInstanceOf(ApiException.class).hasMessageContaining("50 pages");
        assertThatThrownBy(() -> PdfText.extract(new byte[2_000_001])).isInstanceOf(ApiException.class).hasMessageContaining("2 MB");
        assertThatThrownBy(() -> PdfText.extract(pdf("x".repeat(17000),1,false))).isInstanceOf(ApiException.class).hasMessageContaining("16 KB");
    }
}
