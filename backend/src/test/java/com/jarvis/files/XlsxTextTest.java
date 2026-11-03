package com.jarvis.files;

import com.jarvis.common.ApiException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class XlsxTextTest {

    static byte[] createXlsx(Map<String, String> entries) throws IOException {
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
    void extractsTabularCellsWithSharedStrings() throws Exception {
        String sharedStrings = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>Item</t></si>"
                + "<si><t>Price</t></si>"
                + "<si><t>Keyboard</t></si>"
                + "</sst>";

        String sheet1 = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>"
                + "<row r=\"1\">"
                + "<c r=\"A1\" t=\"s\"><v>0</v></c>"
                + "<c r=\"B1\" t=\"s\"><v>1</v></c>"
                + "</row>"
                + "<row r=\"2\">"
                + "<c r=\"A2\" t=\"s\"><v>2</v></c>"
                + "<c r=\"B2\"><v>99.99</v></c>"
                + "</row>"
                + "</sheetData>"
                + "</worksheet>";

        byte[] xlsx = createXlsx(Map.of(
                "xl/sharedStrings.xml", sharedStrings,
                "xl/worksheets/sheet1.xml", sheet1
        ));

        String extracted = XlsxText.extract(xlsx);
        assertThat(extracted).contains("[Sheet 1]");
        assertThat(extracted).contains("Item\tPrice");
        assertThat(extracted).contains("Keyboard\t99.99");
    }

    @Test
    void rejectsMissingOrMalformedSheets() throws Exception {
        assertThatThrownBy(() -> XlsxText.extract(createXlsx(Map.of("xl/other.xml", "data"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no worksheet data");

        assertThatThrownBy(() -> XlsxText.extract(createXlsx(Map.of("xl/worksheets/sheet1.xml", "<bad-xml"))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refusesExpansionAndDeepNesting() throws Exception {
        String deepXml = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "<row>".repeat(130) + "</row>".repeat(130) + "</sheetData></worksheet>";

        assertThatThrownBy(() -> XlsxText.extract(createXlsx(Map.of("xl/worksheets/sheet1.xml", deepXml))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nesting");
    }
}
