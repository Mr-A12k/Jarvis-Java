package com.jarvis.files;

import com.jarvis.common.ApiException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocxTextTest {
    static String xml(String body) {
        return "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"+body+"</w:body></w:document>";
    }
    static byte[] archive(String name,String content) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    @Test void extractsParagraphsSplitRunsAndTables() throws Exception {
        String body="<w:p><w:r><w:t>Hello </w:t></w:r><w:r><w:t>தமிழ்</w:t><w:tab/><w:t>world</w:t><w:br/><w:t>next</w:t></w:r></w:p>"
            +"<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Budget 400</w:t></w:r></w:p></w:tc></w:tr></w:tbl>";
        assertThat(DocxText.extract(archive("word/document.xml",xml(body)))).isEqualTo("Hello தமிழ்\tworld\nnext\nBudget 400");
    }
    @Test void rejectsMissingMalformedAndEmptyDocuments() throws Exception {
        assertThatThrownBy(() -> DocxText.extract(archive("other.xml","text"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DocxText.extract(archive("word/document.xml","<bad>"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DocxText.extract(archive("word/document.xml",xml("")))).isInstanceOf(ApiException.class);
    }
    @Test void refusesExternalEntitiesAndExpansion() throws Exception {
        String external="<!DOCTYPE document [<!ENTITY x SYSTEM 'file:///should-never-be-read'>]>"+xml("<w:p><w:r><w:t>&x;</w:t></w:r></w:p>");
        assertThatThrownBy(() -> DocxText.extract(archive("word/document.xml",external))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DocxText.extract(archive("word/document.xml","x".repeat(1_000_001)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DocxText.extract(archive("media.bin","x".repeat(8_000_001)))).isInstanceOf(ApiException.class);
    }
    @Test void rejectsOversizeExtractedTextAndDeepXml() throws Exception {
        assertThatThrownBy(() -> DocxText.extract(archive("word/document.xml",xml("<w:p><w:r><w:t>"+"界".repeat(5400)+"</w:t></w:r></w:p>"))))
            .isInstanceOf(ApiException.class).hasMessageContaining("16 KB");
        assertThatThrownBy(() -> DocxText.extract(archive("word/document.xml",xml("<w:p>".repeat(130)+"</w:p>".repeat(130)))))
            .isInstanceOf(ApiException.class).hasMessageContaining("nesting");
    }
}
