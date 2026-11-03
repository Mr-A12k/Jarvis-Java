package com.jarvis.files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import javax.xml.stream.*;

/** Bounded main-document text extraction; never follows relationships or extracts files. */
final class DocxText {
    static final int MAX_FILE_BYTES = 2_000_000;
    static String extract(byte[] bytes) throws IOException {
        byte[] document = null;
        int count = 0, expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 1000) throw LocalFiles.bad("DOCX contains too many ZIP entries.");
                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int read;
                boolean main = entry.getName().equals("word/document.xml");
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 8_000_000) throw LocalFiles.bad("DOCX expanded content exceeds 8 MB.");
                    if (main) {
                        if (output.size() + read > 1_000_000) throw LocalFiles.bad("DOCX document XML exceeds 1 MB.");
                        output.write(buffer, 0, read);
                    }
                }
                if (main) {
                    if (document != null) throw LocalFiles.bad("DOCX contains duplicate document parts.");
                    document = output.toByteArray();
                }
            }
        } catch (ZipException e) { throw LocalFiles.bad("Invalid or encrypted DOCX archive."); }
        if (document == null) throw LocalFiles.bad("DOCX is missing its main document text.");
        return parse(document);
    }

    private static String parse(byte[] xml) {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> { throw new XMLStreamException("External resources are disabled"); });
        var text = new StringBuilder();
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            boolean inText = false, root = false;
            int depth = 0, textBytes = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE)
                    throw LocalFiles.bad("DOCX entities and DTDs are unsupported.");
                String addition = "";
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > 128) throw LocalFiles.bad("DOCX XML nesting is too deep.");
                    if (depth == 1) root = word(reader) && reader.getLocalName().equals("document");
                    if (word(reader)) {
                        switch (reader.getLocalName()) {
                            case "t" -> inText = true;
                            case "tab" -> addition = "\t";
                            case "br", "cr" -> addition = "\n";
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (word(reader)) {
                        if (reader.getLocalName().equals("t")) inText = false;
                        if (reader.getLocalName().equals("p")) addition = "\n";
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && inText) {
                    addition = reader.getText();
                }
                textBytes += addition.getBytes(StandardCharsets.UTF_8).length;
                if (textBytes > 16000) throw LocalFiles.bad("DOCX extracted text exceeds 16 KB.");
                text.append(addition);
            }
            if (!root || text.toString().isBlank()) throw LocalFiles.bad("DOCX contains no supported main-document text.");
            return text.toString().strip();
        } catch (XMLStreamException e) { throw LocalFiles.bad("Invalid or unsupported DOCX XML."); }
        finally { if (reader != null) try { reader.close(); } catch (XMLStreamException ignored) {} }
    }

    private static boolean word(XMLStreamReader reader) {
        return "http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(reader.getNamespaceURI())
            || "http://purl.oclc.org/ooxml/wordprocessingml/main".equals(reader.getNamespaceURI());
    }
}
