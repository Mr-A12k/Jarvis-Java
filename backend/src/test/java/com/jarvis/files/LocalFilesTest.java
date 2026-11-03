package com.jarvis.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class LocalFilesTest {
    @Test void pdfDocumentsAreIndexedAndRemainReadOnly() throws Exception {
        Path path=root.resolve("plan.PDF");
        byte[] original=PdfTextTest.pdf("Project budget is 400.",1,false);
        Files.write(path,original);
        assertThat(files.browse(root.toString()).entries()).hasSize(1);
        var doc=files.read(path.toString());
        assertThat(doc.content()).contains("budget is 400");
        files.index(root.toString());
        assertThat(files.retrieve("budget",doc.path())).hasSize(1);
        assertThatThrownBy(() -> files.propose(doc.path(),doc.hash(),"replacement")).isInstanceOf(ApiException.class);
        var chat=org.mockito.Mockito.mock(com.jarvis.chat.ChatService.class);
        reactor.test.StepVerifier.create(new FileController(files,chat).suggest(new FileController.AiEdit(doc.path(),doc.hash(),"rewrite")))
            .expectError(ApiException.class).verify();
        org.mockito.Mockito.verifyNoInteractions(chat);
        assertThat(Files.readAllBytes(path)).isEqualTo(original);
        Files.write(path,PdfTextTest.pdf("Project budget is 900.",1,false));
        assertThat(files.retrieve("budget",doc.path()).getFirst().excerpt()).contains("900");
    }
    @Test void wordDocumentsCanBePreviewedIndexedAndQueriedButNotEdited() throws Exception {
        Path path=root.resolve("plan.DOCX");
        Files.write(path,DocxTextTest.archive("word/document.xml",DocxTextTest.xml("<w:p><w:r><w:t>Project budget is 400.</w:t></w:r></w:p>")));
        byte[] original=Files.readAllBytes(path);
        assertThat(files.browse(root.toString()).entries()).hasSize(1);
        var doc=files.read(path.toString()); assertThat(doc.content()).contains("budget is 400");
        files.index(root.toString());
        assertThat(files.retrieve("budget",doc.path())).hasSize(1);
        assertThatThrownBy(() -> files.propose(doc.path(),doc.hash(),"replacement")).isInstanceOf(ApiException.class).hasMessageContaining("preview");
        var chat=org.mockito.Mockito.mock(com.jarvis.chat.ChatService.class);
        reactor.test.StepVerifier.create(new FileController(files,chat).suggest(new FileController.AiEdit(doc.path(),doc.hash(),"rewrite")))
            .expectError(ApiException.class).verify();
        org.mockito.Mockito.verifyNoInteractions(chat);
        assertThat(Files.readAllBytes(path)).isEqualTo(original);
    }
    Path root; LocalFiles files;
    @BeforeEach void setup() throws Exception {
        root=Files.createDirectory(Path.of("..","file-test-"+UUID.randomUUID()).toAbsolutePath().normalize());
        files=new LocalFiles(new ObjectMapper(),root.resolve("data/memory.json").toString());
    }
    @AfterEach void cleanup() throws Exception { try(var paths=Files.walk(root)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); } }
    Path write(String name,String text) throws Exception { Path path=root.resolve(name); Files.writeString(path,text); return path; }
    @Test void persistsIndexAndMemoryAndReadsFreshEvidence() throws Exception {
        Path p=write("requirements.md","Project colour is blue."); files.index(root.toString());
        var state=files.state(); files.notes("Prefer concise answers",state.version());
        var reopened=new LocalFiles(new ObjectMapper(),root.resolve("data/memory.json").toString());
        assertThat(reopened.state().notes()).isEqualTo("Prefer concise answers");
        Files.writeString(p,"Project colour is green.");
        assertThat(reopened.retrieve("project colour").getFirst().excerpt()).contains("green");
        Files.delete(p); assertThat(reopened.retrieve("project")).isEmpty();
    }
    @Test void proposalsDoNotWriteUntilAppliedAndBackupOriginal() throws Exception {
        Path p=write("example.txt","Original"); var d=files.read(p.toString());
        var proposal=files.propose(p.toString(),d.hash(),"Updated"); assertThat(Files.readString(p)).isEqualTo("Original");
        assertThat(files.apply(proposal.id()).content()).isEqualTo("Updated");
        assertThat(Files.readString(root.resolve("data/backups/"+proposal.id()+".txt"))).isEqualTo("Original");
        assertThatThrownBy(()->files.apply(proposal.id())).isInstanceOf(ApiException.class);
    }
    @Test void concurrentExternalChangeIsNeverOverwritten() throws Exception {
        Path p=write("example.md","Original"); var d=files.read(p.toString()); var proposal=files.propose(p.toString(),d.hash(),"Proposed");
        Files.writeString(p,"Changed outside JARVIS"); assertThatThrownBy(()->files.apply(proposal.id())).isInstanceOf(ApiException.class);
        assertThat(Files.readString(p)).isEqualTo("Changed outside JARVIS");
    }
    @Test void blocksCredentialsTraversalBinaryAndOversize() throws Exception {
        for(String name:List.of(".env","passwords.txt","script.ps1")) { var p=write(name,"secret"); assertThatThrownBy(()->files.read(p.toString())).isInstanceOf(ApiException.class); }
        var binary=write("binary.txt","a\0b"); assertThatThrownBy(()->files.read(binary.toString())).isInstanceOf(ApiException.class);
        var big=write("big.txt","x".repeat(16001)); assertThatThrownBy(()->files.read(big.toString())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->files.read(root.resolve("../elsewhere.txt").toString())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->files.browse("\\\\server\\share")).isInstanceOf(ApiException.class);
    }
    @Test void memoryVersionConflictsAndForgetting() throws Exception {
        Path p=write("notes.txt","Project banana");files.index(root.toString()); var old=files.state(); files.notes("Short answers",old.version());
        assertThatThrownBy(()->files.notes("Old overwrite",old.version())).isInstanceOf(ApiException.class);
        files.forget(p.toString(),files.state().version()); assertThat(files.retrieve("banana")).isEmpty(); assertThat(Files.exists(p)).isTrue();
    }
    @Test void listingAndIndexSkipUnsupportedAndHiddenFiles() throws Exception {
        write("ok.md","Evidence");write("secret.txt","no");Files.createDirectory(root.resolve(".hidden"));
        assertThat(files.browse(root.toString()).entries()).hasSize(1);
        assertThat(files.index(root.toString())).isEqualTo(1);
    }
    @Test void selectedDocumentIsFilteredBeforeRankingAndMustStayIndexed() throws Exception {
        Path selected=write("selected.md","Project colour is green.");
        write("other.md","Project colour blue.\nProject colour red.\nProject colour orange.");
        files.index(root.toString());
        String path=files.read(selected.toString()).path();
        assertThat(files.retrieve("project colour",path)).hasSize(1)
            .allSatisfy(hit -> {
                assertThat(hit.path()).startsWith(path+":");
                assertThat(hit.excerpt()).contains("green");
            });
        assertThat(files.retrieve("orange",path)).isEmpty();
        files.forget(path,files.state().version());
        assertThatThrownBy(() -> files.retrieve("project",path)).isInstanceOf(ApiException.class);
    }
}
