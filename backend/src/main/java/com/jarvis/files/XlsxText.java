package com.jarvis.files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.stream.*;

/**
 * Bounded XLSX spreadsheet extraction using safe XML stream parsing;
 * never executes macros, scripts, external links or images.
 */
final class XlsxText {
    static final int MAX_FILE_BYTES = 2_000_000;

    static String extract(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw LocalFiles.bad("XLSX file is empty.");
        if (bytes.length > MAX_FILE_BYTES) throw LocalFiles.bad("XLSX exceeds 2 MB.");

        byte[] sharedStringsXml = null;
        Map<String, byte[]> sheetXmls = new TreeMap<>();
        int count = 0, expanded = 0;

        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 1000) throw LocalFiles.bad("XLSX contains too many ZIP entries.");
                String name = entry.getName();
                boolean isShared = name.equals("xl/sharedStrings.xml");
                boolean isSheet = name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml");

                if (!isShared && !isSheet) continue;

                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 8_000_000) throw LocalFiles.bad("XLSX expanded content exceeds 8 MB.");
                    if (output.size() + read > 2_000_000) throw LocalFiles.bad("XLSX sheet XML exceeds 2 MB.");
                    output.write(buffer, 0, read);
                }

                if (isShared) {
                    if (sharedStringsXml != null) throw LocalFiles.bad("XLSX contains duplicate shared strings.");
                    sharedStringsXml = output.toByteArray();
                } else {
                    sheetXmls.put(name, output.toByteArray());
                }
            }
        } catch (ZipException e) {
            throw LocalFiles.bad("Invalid or encrypted XLSX archive.");
        }

        if (sheetXmls.isEmpty()) throw LocalFiles.bad("XLSX contains no worksheet data.");

        List<String> sharedStrings = sharedStringsXml != null ? parseSharedStrings(sharedStringsXml) : List.of();
        StringBuilder fullText = new StringBuilder();
        int sheetNum = 1;
        int totalTextBytes = 0;

        for (var entry : sheetXmls.entrySet()) {
            if (sheetNum > 1) fullText.append("\n\n");
            fullText.append("[Sheet ").append(sheetNum++).append("]\n");
            totalTextBytes += fullText.length();

            String sheetContent = parseSheet(entry.getValue(), sharedStrings, 16000 - totalTextBytes);
            fullText.append(sheetContent);
            totalTextBytes += sheetContent.getBytes(StandardCharsets.UTF_8).length;
            if (totalTextBytes >= 16000) break;
        }

        String result = fullText.toString().strip();
        if (result.isBlank()) throw LocalFiles.bad("XLSX contains no readable cell text.");
        return result;
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

    private static List<String> parseSharedStrings(byte[] xml) {
        var list = new ArrayList<String>();
        XMLStreamReader reader = null;
        try {
            reader = secureFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
            StringBuilder current = new StringBuilder();
            boolean inT = false;
            int depth = 0;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > 128) throw LocalFiles.bad("XLSX XML nesting is too deep.");
                    if ("si".equals(reader.getLocalName())) {
                        current.setLength(0);
                    } else if ("t".equals(reader.getLocalName())) {
                        inT = true;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if ("t".equals(reader.getLocalName())) {
                        inT = false;
                    } else if ("si".equals(reader.getLocalName())) {
                        list.add(current.toString().strip());
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && inT) {
                    current.append(reader.getText());
                }
            }
        } catch (XMLStreamException e) {
            throw LocalFiles.bad("Invalid XLSX shared strings XML.");
        } finally {
            if (reader != null) try { reader.close(); } catch (XMLStreamException ignored) {}
        }
        return list;
    }

    private static String parseSheet(byte[] xml, List<String> sharedStrings, int remainingBytes) {
        var sheetBuilder = new StringBuilder();
        XMLStreamReader reader = null;
        try {
            reader = secureFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
            String cellType = null;
            boolean inV = false;
            boolean inT = false;
            StringBuilder valBuilder = new StringBuilder();
            int rowCells = 0;
            int textBytes = 0;
            int depth = 0;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > 128) throw LocalFiles.bad("XLSX XML nesting is too deep.");
                    String name = reader.getLocalName();
                    if ("row".equals(name)) {
                        rowCells = 0;
                    } else if ("c".equals(name)) {
                        cellType = reader.getAttributeValue(null, "t");
                        valBuilder.setLength(0);
                    } else if ("v".equals(name)) {
                        inV = true;
                    } else if ("t".equals(name)) {
                        inT = true;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    String name = reader.getLocalName();
                    if ("v".equals(name)) {
                        inV = false;
                    } else if ("t".equals(name)) {
                        inT = false;
                    } else if ("c".equals(name)) {
                        String rawVal = valBuilder.toString().strip();
                        String cellVal = "";
                        if ("s".equals(cellType)) {
                            try {
                                int idx = Integer.parseInt(rawVal);
                                if (idx >= 0 && idx < sharedStrings.size()) {
                                    cellVal = sharedStrings.get(idx);
                                }
                            } catch (NumberFormatException ignored) {}
                        } else {
                            cellVal = rawVal;
                        }

                        if (!cellVal.isBlank()) {
                            if (rowCells > 0) sheetBuilder.append("\t");
                            sheetBuilder.append(cellVal);
                            textBytes += cellVal.getBytes(StandardCharsets.UTF_8).length;
                            rowCells++;
                            if (textBytes > remainingBytes) {
                                throw LocalFiles.bad("XLSX extracted text exceeds 16 KB.");
                            }
                        }
                    } else if ("row".equals(name)) {
                        if (rowCells > 0) {
                            sheetBuilder.append("\n");
                        }
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)) {
                    if (inV || inT) {
                        valBuilder.append(reader.getText());
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw LocalFiles.bad("Invalid XLSX sheet XML.");
        } finally {
            if (reader != null) try { reader.close(); } catch (XMLStreamException ignored) {}
        }
        return sheetBuilder.toString();
    }
}
