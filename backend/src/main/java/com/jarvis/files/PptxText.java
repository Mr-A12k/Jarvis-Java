package com.jarvis.files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.stream.*;

/**
 * Bounded PPTX presentation extraction using safe XML stream parsing;
 * never executes VBA macros, embedded media, audio or external resources.
 */
final class PptxText {
    static final int MAX_FILE_BYTES = 2_000_000;

    static String extract(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw LocalFiles.bad("PPTX file is empty.");
        if (bytes.length > MAX_FILE_BYTES) throw LocalFiles.bad("PPTX exceeds 2 MB.");

        Map<String, byte[]> slideXmls = new TreeMap<>(Comparator.comparingInt(PptxText::extractSlideNumber));
        int count = 0, expanded = 0;

        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 1000) throw LocalFiles.bad("PPTX contains too many ZIP entries.");
                String name = entry.getName();
                if (!name.startsWith("ppt/slides/slide") || !name.endsWith(".xml")) continue;

                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 8_000_000) throw LocalFiles.bad("PPTX expanded content exceeds 8 MB.");
                    if (output.size() + read > 2_000_000) throw LocalFiles.bad("PPTX slide XML exceeds 2 MB.");
                    output.write(buffer, 0, read);
                }
                slideXmls.put(name, output.toByteArray());
            }
        } catch (ZipException e) {
            throw LocalFiles.bad("Invalid or encrypted PPTX archive.");
        }

        if (slideXmls.isEmpty()) throw LocalFiles.bad("PPTX contains no slide data.");

        StringBuilder fullText = new StringBuilder();
        int slideIndex = 1;
        int totalTextBytes = 0;

        for (var entry : slideXmls.entrySet()) {
            if (slideIndex > 1) fullText.append("\n\n");
            fullText.append("--- Slide ").append(slideIndex++).append(" ---\n");
            totalTextBytes += fullText.length();

            String slideContent = parseSlide(entry.getValue(), 16000 - totalTextBytes);
            fullText.append(slideContent);
            totalTextBytes += slideContent.getBytes(StandardCharsets.UTF_8).length;
            if (totalTextBytes >= 16000) break;
        }

        String result = fullText.toString().strip();
        if (result.isBlank()) throw LocalFiles.bad("PPTX contains no readable slide text.");
        return result;
    }

    private static int extractSlideNumber(String slidePath) {
        try {
            // e.g. "ppt/slides/slide12.xml" -> 12
            String num = slidePath.replaceAll("\\D+", "");
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static XMLInputFactory secureFactory() {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> {
            throw new XMLStreamException("External resources are disabled");
        });
        return factory;
    }

    private static String parseSlide(byte[] xml, int remainingBytes) {
        var slideBuilder = new StringBuilder();
        XMLStreamReader reader = null;
        try {
            reader = secureFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
            boolean inText = false;
            int depth = 0;
            int textBytes = 0;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > 128) throw LocalFiles.bad("PPTX XML nesting is too deep.");
                    String name = reader.getLocalName();
                    if ("t".equals(name)) {
                        inText = true;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    String name = reader.getLocalName();
                    if ("t".equals(name)) {
                        inText = false;
                    } else if ("p".equals(name)) {
                        slideBuilder.append("\n");
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && inText) {
                    String addition = reader.getText();
                    textBytes += addition.getBytes(StandardCharsets.UTF_8).length;
                    if (textBytes > remainingBytes) {
                        throw LocalFiles.bad("PPTX extracted text exceeds 16 KB.");
                    }
                    slideBuilder.append(addition);
                }
            }
        } catch (XMLStreamException e) {
            throw LocalFiles.bad("Invalid PPTX slide XML.");
        } finally {
            if (reader != null) try { reader.close(); } catch (XMLStreamException ignored) {}
        }
        return slideBuilder.toString().strip();
    }
}
