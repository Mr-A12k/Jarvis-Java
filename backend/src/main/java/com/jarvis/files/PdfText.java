package com.jarvis.files;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Text-only PDF ingestion with page-accurate section markers; no rendering, scripts, attachments or OCR. */
final class PdfText {
    static final int MAX_FILE_BYTES = 2_000_000;

    static String extract(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw LocalFiles.bad("PDF file is empty.");
        if (bytes.length > MAX_FILE_BYTES) throw LocalFiles.bad("PDF exceeds 2 MB.");

        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw LocalFiles.bad("Encrypted PDFs are not supported.");
            int numPages = document.getNumberOfPages();
            if (numPages > 50) throw LocalFiles.bad("PDF exceeds 50 pages. Split it into smaller files.");

            var output = new LimitedWriter();
            var stripper = new LimitedStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");

            for (int p = 1; p <= numPages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                var pageWriter = new LimitedWriter();
                stripper.writeText(document, pageWriter);
                String pageText = pageWriter.text.toString().strip();
                if (!pageText.isBlank()) {
                    if (output.text.length() > 0) output.writePart("\n\n");
                    output.writePart("--- Page " + p + " ---\n");
                    output.writePart(pageText);
                }
            }

            String result = output.text.toString().strip();
            if (result.isBlank()) throw LocalFiles.bad("PDF has no extractable text. Scanned documents need OCR, which is not supported yet.");
            return result;
        } catch (InvalidPasswordException e) {
            throw LocalFiles.bad("Password-protected PDFs are not supported.");
        } catch (IOException e) {
            throw LocalFiles.bad("Invalid or unsupported PDF. Try exporting a fresh text-based PDF.");
        }
    }

    private static final class LimitedStripper extends PDFTextStripper {
        private int glyphs, operators;
        private final long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        LimitedStripper() throws IOException { super(); }
        @Override protected void processTextPosition(TextPosition text) {
            if (++glyphs > 20000) throw LocalFiles.bad("PDF text is too large to index. Split it into smaller files.");
            super.processTextPosition(text);
        }
        @Override protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (++operators > 100000 || System.nanoTime() > deadline || Thread.currentThread().isInterrupted())
                throw LocalFiles.bad("PDF is too complex to extract within the processing limit.");
            super.processOperator(operator, operands);
        }
    }

    private static final class LimitedWriter extends Writer {
        final StringBuilder text = new StringBuilder();
        private int bytes;

        void writePart(String part) {
            bytes += part.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 16000) throw LocalFiles.bad("PDF extracted text exceeds 16 KB. Split it into smaller files.");
            text.append(part);
        }

        @Override public void write(char[] chars, int offset, int length) {
            writePart(new String(chars, offset, length));
        }
        @Override public void flush() {}
        @Override public void close() {}
    }
}
