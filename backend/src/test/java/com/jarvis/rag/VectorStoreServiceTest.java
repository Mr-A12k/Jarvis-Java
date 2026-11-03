package com.jarvis.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.database.DatabaseStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VectorStoreServiceTest {
    @Test void changedOrLegacyModelReembedsUnchangedContent() throws Exception {
        Connection conn = availableConnection();
        String content="Same document text";
        var check=mock(PreparedStatement.class); var rows=mock(ResultSet.class);
        when(conn.prepareStatement(contains("SELECT id, content_hash, chunk_count,"))).thenReturn(check);
        when(check.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("content_hash")).thenReturn(DocumentChunker.sha256(content));
        when(rows.getBoolean("model_matches")).thenReturn(false);
        when(rows.getObject("id")).thenReturn(UUID.randomUUID());
        var update=mock(PreparedStatement.class); var delete=mock(PreparedStatement.class); var insert=mock(PreparedStatement.class);
        when(conn.prepareStatement(contains("UPDATE jarvis.documents"))).thenReturn(update);
        when(conn.prepareStatement(contains("DELETE FROM jarvis.document_chunks"))).thenReturn(delete);
        when(conn.prepareStatement(contains("INSERT INTO jarvis.document_chunks"))).thenReturn(insert);
        when(embeddingService.embed(anyString())).thenReturn(Mono.just(new float[384]));
        assertThat(vectorStore.indexDocument("doc.md","doc.md",content).block()).isEqualTo(1);
        verify(embeddingService).embed(anyString());
        verify(delete).executeUpdate(); verify(insert).executeBatch();
        verify(check,times(2)).setString(1,"all-minilm:22m");
    }

    @Test void invalidVectorsOrEmbeddingFailureNeverDeleteExistingChunks() throws Exception {
        Connection conn=availableConnection();
        var check=mock(PreparedStatement.class); var rows=mock(ResultSet.class);
        when(conn.prepareStatement(contains("SELECT id, content_hash, chunk_count,"))).thenReturn(check);
        when(check.executeQuery()).thenReturn(rows); when(rows.next()).thenReturn(false);
        when(embeddingService.embed(anyString())).thenReturn(Mono.just(new float[768]));
        StepVerifier.create(vectorStore.indexDocument("doc.md","doc.md","text"))
            .expectErrorSatisfies(e -> assertThat(((com.jarvis.common.ApiException)e).code()).isEqualTo("EMBEDDING_DIMENSIONS")).verify();
        when(embeddingService.embed(anyString())).thenReturn(Mono.error(new IllegalStateException("offline")));
        StepVerifier.create(vectorStore.indexDocument("doc.md","doc.md","text")).expectError().verify();
        verify(conn,never()).prepareStatement(contains("DELETE FROM"));
        verify(conn,never()).prepareStatement(contains("UPDATE jarvis.documents"));
    }

    @Test void unsupportedConfiguredDimensionsFailBeforeInference() throws Exception {
        availableConnection(); when(embeddingService.dimensions()).thenReturn(768);
        StepVerifier.create(vectorStore.indexDocument("doc.md","doc.md","text"))
            .expectErrorSatisfies(e -> assertThat(((com.jarvis.common.ApiException)e).code()).isEqualTo("EMBEDDING_DIMENSIONS")).verify();
        verify(embeddingService,never()).embed(anyString());
    }

    private Connection availableConnection() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        var conn=mock(Connection.class); var ext=mock(Statement.class); var rows=mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(ext); when(ext.executeQuery(anyString())).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(databaseStore.execute(any())).thenAnswer(inv -> ((DatabaseStore.Work<?>)inv.getArgument(0)).run(conn));
        return conn;
    }
    @Test
    void scopedSearchBindsPathAndFiltersBeforeLimit() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        Connection conn=mock(Connection.class);
        Statement ext=mock(Statement.class);
        ResultSet extRows=mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(ext);
        when(ext.executeQuery(anyString())).thenReturn(extRows);
        when(extRows.next()).thenReturn(true);
        when(databaseStore.execute(any())).thenAnswer(inv -> ((DatabaseStore.Work<?>)inv.getArgument(0)).run(conn));
        when(embeddingService.embed("deadline")).thenReturn(Mono.just(new float[384]));
        PreparedStatement search=mock(PreparedStatement.class);
        ResultSet rows=mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(search);
        when(search.executeQuery()).thenReturn(rows);
        assertThat(vectorStore.search("deadline",3,0.45,"selected.md").block()).isEmpty();
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("c.owner_name = session_user", "d.owner_name = session_user")
            .containsPattern("(?s)WHERE.*d.file_path = \\?.*ORDER BY.*LIMIT");
        verify(search).setString(2,"selected.md");
        verify(search).setString(4,"all-minilm:22m");
        verify(search).setString(5,"384");
        assertThat(sql.getValue()).contains("metadata->>'embeddingModel' = ?", "metadata->>'embeddingDimensions' = ?");
        verify(search).setInt(8,3);
    }
    private DatabaseStore databaseStore;
    private EmbeddingService embeddingService;
    private DocumentChunker chunker;
    private ObjectMapper objectMapper;
    private VectorStoreService vectorStore;

    @BeforeEach
    void setUp() {
        databaseStore = mock(DatabaseStore.class);
        embeddingService = mock(EmbeddingService.class);
        when(embeddingService.modelName()).thenReturn("all-minilm:22m");
        when(embeddingService.dimensions()).thenReturn(384);
        chunker = new DocumentChunker();
        objectMapper = new ObjectMapper();
        vectorStore = new VectorStoreService(databaseStore, embeddingService, chunker, objectMapper);
    }

    @Test
    void isAvailableReturnsFalseWhenDatabaseNotConfigured() {
        when(databaseStore.configured()).thenReturn(false);
        assertThat(vectorStore.isAvailable()).isFalse();
    }

    @Test
    void isAvailableReturnsFalseWhenDatabaseStoreIsNull() {
        var service = new VectorStoreService(null, embeddingService, chunker, objectMapper);
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailableReturnsTrueWhenVectorExtensionExists() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'")).thenReturn(rs);
        when(rs.next()).thenReturn(true);

        when(databaseStore.execute(any())).thenAnswer(invocation -> {
            DatabaseStore.Work<?> work = invocation.getArgument(0);
            return work.run(conn);
        });

        assertThat(vectorStore.isAvailable()).isTrue();
    }

    @Test
    void isAvailableReturnsFalseWhenVectorExtensionMissing() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        when(databaseStore.execute(any())).thenAnswer(invocation -> {
            DatabaseStore.Work<?> work = invocation.getArgument(0);
            return work.run(conn);
        });

        assertThat(vectorStore.isAvailable()).isFalse();
    }

    @Test
    void indexDocumentReturnsZeroWhenUnavailable() {
        when(databaseStore.configured()).thenReturn(false);

        StepVerifier.create(vectorStore.indexDocument("test.md", "test.md", "Some test content"))
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    void indexDocumentReturnsZeroForBlankContent() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(databaseStore.execute(any())).thenAnswer(inv -> ((DatabaseStore.Work<?>) inv.getArgument(0)).run(conn));

        StepVerifier.create(vectorStore.indexDocument("test.md", "test.md", "   \n  "))
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    void indexDocumentDeduplicatesWhenHashMatches() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        Connection conn = mock(Connection.class);
        Statement extStmt = mock(Statement.class);
        ResultSet extRs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(extStmt);
        when(extStmt.executeQuery(anyString())).thenReturn(extRs);
        when(extRs.next()).thenReturn(true);

        when(databaseStore.execute(any())).thenAnswer(inv -> ((DatabaseStore.Work<?>) inv.getArgument(0)).run(conn));

        String content = "Hello world from JARVIS vector indexing";
        String expectedHash = DocumentChunker.sha256(content);

        PreparedStatement checkStmt = mock(PreparedStatement.class);
        ResultSet checkRs = mock(ResultSet.class);
        when(conn.prepareStatement(contains("SELECT id, content_hash, chunk_count,"))).thenReturn(checkStmt);
        when(checkStmt.executeQuery()).thenReturn(checkRs);
        when(checkRs.next()).thenReturn(true);
        when(checkRs.getString("content_hash")).thenReturn(expectedHash);
        when(checkRs.getInt("chunk_count")).thenReturn(1);
        when(checkRs.getBoolean("model_matches")).thenReturn(true);

        float[] sampleEmbedding = new float[384];
        when(embeddingService.embed(anyString())).thenReturn(Mono.just(sampleEmbedding));

        StepVerifier.create(vectorStore.indexDocument("doc.txt", "doc.txt", content))
                .expectNext(1)
                .verifyComplete();

        // Verify that no chunk deletion or re-insertion occurred because hashes matched
        verify(conn, never()).prepareStatement(contains("DELETE FROM jarvis.document_chunks"));
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void indexDocumentInsertsNewDocumentAndChunks() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        Connection conn = mock(Connection.class);
        Statement extStmt = mock(Statement.class);
        ResultSet extRs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(extStmt);
        when(extStmt.executeQuery(anyString())).thenReturn(extRs);
        when(extRs.next()).thenReturn(true);

        when(databaseStore.execute(any())).thenAnswer(inv -> ((DatabaseStore.Work<?>) inv.getArgument(0)).run(conn));

        String content = "New document content for vector search verification";
        PreparedStatement checkStmt = mock(PreparedStatement.class);
        ResultSet checkRs = mock(ResultSet.class);
        when(conn.prepareStatement(contains("SELECT id, content_hash, chunk_count,"))).thenReturn(checkStmt);
        when(checkStmt.executeQuery()).thenReturn(checkRs);
        when(checkRs.next()).thenReturn(false); // Does not exist

        PreparedStatement insDocStmt = mock(PreparedStatement.class);
        ResultSet insDocRs = mock(ResultSet.class);
        when(conn.prepareStatement(contains("INSERT INTO jarvis.documents"))).thenReturn(insDocStmt);
        when(insDocStmt.executeQuery()).thenReturn(insDocRs);
        when(insDocRs.next()).thenReturn(true);
        UUID newDocId = UUID.randomUUID();
        when(insDocRs.getObject(1)).thenReturn(newDocId);

        PreparedStatement insChunkStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(contains("INSERT INTO jarvis.document_chunks"))).thenReturn(insChunkStmt);
        when(insChunkStmt.executeBatch()).thenReturn(new int[]{1});

        float[] sampleEmbedding = new float[384];
        when(embeddingService.embed(anyString())).thenReturn(Mono.just(sampleEmbedding));

        StepVerifier.create(vectorStore.indexDocument("notes.txt", "notes.txt", content))
                .expectNext(1)
                .verifyComplete();

        verify(insChunkStmt).executeBatch();
        var metadata=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(insChunkStmt).setString(eq(5),metadata.capture());
        assertThat(objectMapper.readTree(metadata.getValue()).path("embeddingModel").asText()).isEqualTo("all-minilm:22m");
        assertThat(objectMapper.readTree(metadata.getValue()).path("embeddingDimensions").asInt()).isEqualTo(384);
    }

    @Test
    void searchReturnsEmptyWhenNotAvailableOrQueryBlank() {
        when(databaseStore.configured()).thenReturn(false);

        StepVerifier.create(vectorStore.search("test query", 5, 0.5))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

        StepVerifier.create(vectorStore.search("  ", 5, 0.5))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
    }

    @Test
    void searchPerformsCosineMatchingAndReturnsHits() throws Exception {
        when(databaseStore.configured()).thenReturn(true);
        Connection conn = mock(Connection.class);
        Statement extStmt = mock(Statement.class);
        ResultSet extRs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(extStmt);
        when(extStmt.executeQuery(anyString())).thenReturn(extRs);
        when(extRs.next()).thenReturn(true);

        when(databaseStore.execute(any())).thenAnswer(inv -> ((DatabaseStore.Work<?>) inv.getArgument(0)).run(conn));

        float[] queryEmbedding = new float[384];
        when(embeddingService.embed("neural networks")).thenReturn(Mono.just(queryEmbedding));

        PreparedStatement searchStmt = mock(PreparedStatement.class);
        ResultSet searchRs = mock(ResultSet.class);
        when(conn.prepareStatement(contains("FROM jarvis.document_chunks c"))).thenReturn(searchStmt);
        when(searchStmt.executeQuery()).thenReturn(searchRs);

        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(searchRs.next()).thenReturn(true, false);
        when(searchRs.getObject("id")).thenReturn(chunkId);
        when(searchRs.getObject("document_id")).thenReturn(docId);
        when(searchRs.getInt("chunk_index")).thenReturn(0);
        when(searchRs.getString("content")).thenReturn("Neural networks learn representations");
        when(searchRs.getDouble("similarity")).thenReturn(0.88);
        when(searchRs.getString("file_path")).thenReturn("/docs/nn.md");
        when(searchRs.getString("file_name")).thenReturn("nn.md");

        StepVerifier.create(vectorStore.search("neural networks", 3, 0.5))
                .assertNext(hits -> {
                    assertThat(hits).hasSize(1);
                    var hit = hits.getFirst();
                    assertThat(hit.id()).isEqualTo(chunkId);
                    assertThat(hit.documentId()).isEqualTo(docId);
                    assertThat(hit.chunkIndex()).isEqualTo(0);
                    assertThat(hit.content()).contains("Neural networks");
                    assertThat(hit.similarity()).isEqualTo(0.88);
                    assertThat(hit.filePath()).isEqualTo("/docs/nn.md");
                    assertThat(hit.fileName()).isEqualTo("nn.md");
                })
                .verifyComplete();
        verify(searchStmt).setString(2,null);
        verify(searchStmt).setString(4,"all-minilm:22m");
        verify(searchStmt).setString(5,"384");
    }
}

