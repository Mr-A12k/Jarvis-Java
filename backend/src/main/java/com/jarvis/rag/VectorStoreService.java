package com.jarvis.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.database.DatabaseStore;
import com.jarvis.common.ApiException;
import org.springframework.http.HttpStatus;
import com.pgvector.PGvector;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service managing pgvector vector store operations:
 * document ingestion, chunk storage, SHA-256 deduplication, and cosine similarity search.
 */
@Service
public class VectorStoreService {
    private static final Logger LOG = LoggerFactory.getLogger(VectorStoreService.class);
    private final DatabaseStore databaseStore;
    private final EmbeddingService embeddingService;
    private final DocumentChunker chunker;
    private final ObjectMapper objectMapper;
    private static final String DOCUMENT_CHECK = """
        SELECT id, content_hash, chunk_count,
          (chunk_count > 0 AND chunk_count = (
            SELECT count(*) FROM jarvis.document_chunks c
            WHERE c.document_id = d.id AND c.owner_name = session_user
              AND c.metadata->>'embeddingModel' = ?
              AND c.metadata->>'embeddingDimensions' = ?
          )) AS model_matches
        FROM jarvis.documents d WHERE file_path = ? AND owner_name = session_user
        """;

    public record VectorHit(UUID id, UUID documentId, int chunkIndex, String content, double similarity, String filePath, String fileName) {}

    public VectorStoreService(DatabaseStore databaseStore, EmbeddingService embeddingService, DocumentChunker chunker, ObjectMapper objectMapper) {
        this.databaseStore = databaseStore;
        this.embeddingService = embeddingService;
        this.chunker = chunker != null ? chunker : new DocumentChunker();
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        if (databaseStore == null || !databaseStore.configured()) return false;
        try {
            return Boolean.TRUE.equals(databaseStore.execute(c -> {
                try (Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'")) {
                    return rs.next();
                } catch (SQLException e) {
                    LOG.debug("pgvector extension check failed: {}", e.getMessage());
                    return false;
                }
            }));
        } catch (Exception e) {
            LOG.debug("DatabaseStore not available for pgvector: {}", e.getMessage());
            return false;
        }
    }

    public Mono<Integer> indexDocument(String filePath, String fileName, String content) {
        return Mono.fromCallable(this::isAvailable).subscribeOn(Schedulers.boundedElastic())
            .flatMap(available -> available ? indexAvailable(filePath, fileName, content) : Mono.just(0));
    }

    private Mono<Integer> indexAvailable(String filePath, String fileName, String content) {
        if (content == null || content.isBlank()) return Mono.just(0);
        requireSupportedDimensions();
        String hash = DocumentChunker.sha256(content);
        var chunkList = chunker.chunk(filePath, content);
        if (chunkList.isEmpty()) return Mono.just(0);

        return Mono.fromCallable(() -> databaseStore.execute(c -> {
            try (PreparedStatement s = documentCheck(c, filePath)) {
                try (ResultSet r = s.executeQuery()) {
                    return r.next() && hash.equals(r.getString("content_hash")) && r.getBoolean("model_matches") ? r.getInt("chunk_count") : -1;
                }
            }
        })).subscribeOn(Schedulers.boundedElastic()).flatMap(existing -> {
            if (existing >= 0) return Mono.just(existing);
            List<float[]> embeddings = new ArrayList<>();
            Mono<Void> chain = Mono.empty();
            for (var chunk : chunkList) {
                chain = chain.then(embeddingService.embed(chunk.content())
                    .switchIfEmpty(Mono.error(dimensionError()))
                    .doOnNext(vector -> { validateVector(vector); embeddings.add(vector); }).then());
            }

            return chain.then(Mono.fromCallable(() -> {
                return databaseStore.execute(c -> {
                    try {
                        PGvector.addVectorType(c);
                    } catch (Throwable t) {
                        LOG.debug("Could not register PGvector type: {}", t.getMessage());
                    }
                    UUID docId;

                    // Deduplication check: check if document already exists with identical SHA-256 hash
                    try (PreparedStatement check = documentCheck(c, filePath)) {
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next()) {
                                String existingHash = rs.getString("content_hash");
                                if (hash.equals(existingHash) && rs.getBoolean("model_matches")) {
                                    LOG.debug("Document {} is unchanged (SHA-256 match); skipping re-embedding", filePath);
                                    return rs.getInt("chunk_count");
                                }

                                // Document content changed; update header and delete outdated chunks
                                docId = (UUID) rs.getObject("id");
                                try (PreparedStatement upd = c.prepareStatement(
                                        "UPDATE jarvis.documents SET file_name = ?, content_hash = ?, chunk_count = ?, status = 'ready', updated_at = now() WHERE id = ?")) {
                                    upd.setString(1, fileName);
                                    upd.setString(2, hash);
                                    upd.setInt(3, chunkList.size());
                                    upd.setObject(4, docId);
                                    upd.executeUpdate();
                                }
                                try (PreparedStatement del = c.prepareStatement("DELETE FROM jarvis.document_chunks WHERE document_id = ?")) {
                                    del.setObject(1, docId);
                                    del.executeUpdate();
                                }
                            } else {
                                // New document
                                try (PreparedStatement ins = c.prepareStatement(
                                        "INSERT INTO jarvis.documents (file_name, file_path, content_hash, status, chunk_count) VALUES (?, ?, ?, 'ready', ?) RETURNING id")) {
                                    ins.setString(1, fileName);
                                    ins.setString(2, filePath);
                                    ins.setString(3, hash);
                                    ins.setInt(4, chunkList.size());
                                    try (ResultSet irs = ins.executeQuery()) {
                                        irs.next();
                                        docId = (UUID) irs.getObject(1);
                                    }
                                }
                            }
                        }
                    }

                    // Insert document chunks with embeddings and metadata
                    String sql = "INSERT INTO jarvis.document_chunks (document_id, chunk_index, content, embedding, metadata) VALUES (?, ?, ?, ?, ?::jsonb)";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        for (int i = 0; i < chunkList.size(); i++) {
                            var chunk = chunkList.get(i);
                            ps.setObject(1, docId);
                            ps.setInt(2, chunk.index());
                            ps.setString(3, chunk.content());
                            ps.setObject(4, new PGvector(embeddings.get(i)));
                            var metadata = new LinkedHashMap<String,Object>(chunk.metadata());
                            metadata.put("embeddingModel", embeddingService.modelName());
                            metadata.put("embeddingDimensions", embeddingService.dimensions());
                            ps.setString(5, toJson(metadata));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    return chunkList.size();
                });
            }).subscribeOn(Schedulers.boundedElastic()));
        });
    }

    public Mono<List<VectorHit>> search(String query, int matchCount, double threshold) {
        return search(query, matchCount, threshold, null);
    }

    public Mono<List<VectorHit>> search(String query, int matchCount, double threshold, String filePath) {
        return Mono.fromCallable(this::isAvailable).subscribeOn(Schedulers.boundedElastic())
            .flatMap(available -> available ? searchAvailable(query, matchCount, threshold, filePath) : Mono.just(List.of()));
    }

    private Mono<List<VectorHit>> searchAvailable(String query, int matchCount, double threshold, String filePath) {
        if (query == null || query.isBlank()) return Mono.just(List.of());
        requireSupportedDimensions();

        return embeddingService.embed(query).flatMap(embedding -> {
            if (embedding == null || embedding.length == 0) return Mono.just(List.<VectorHit>of());
            validateVector(embedding);

            return Mono.fromCallable(() -> {
                return databaseStore.execute(c -> {
                    try {
                        PGvector.addVectorType(c);
                    } catch (Throwable t) {
                        LOG.debug("Could not register PGvector type: {}", t.getMessage());
                    }
                    String sql = """
                        SELECT c.id, c.document_id, c.chunk_index, c.content,
                               1 - (c.embedding OPERATOR(public.<=>) ?) AS similarity,
                               d.file_path, d.file_name
                        FROM jarvis.document_chunks c
                        JOIN jarvis.documents d ON d.id = c.document_id
                        WHERE c.owner_name = session_user AND d.owner_name = session_user
                          AND (?::text IS NULL OR d.file_path = ?)
                          AND c.metadata->>'embeddingModel' = ?
                          AND c.metadata->>'embeddingDimensions' = ?
                          AND 1 - (c.embedding OPERATOR(public.<=>) ?) >= ?
                        ORDER BY similarity DESC LIMIT ?
                        """;
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setObject(1, new PGvector(embedding));
                        ps.setString(2, filePath);
                        ps.setString(3, filePath);
                        ps.setString(4, embeddingService.modelName());
                        ps.setString(5, Integer.toString(embeddingService.dimensions()));
                        ps.setObject(6, new PGvector(embedding));
                        ps.setDouble(7, threshold);
                        ps.setInt(8, matchCount);
                        try (ResultSet rs = ps.executeQuery()) {
                            List<VectorHit> hits = new ArrayList<>();
                            while (rs.next()) {
                                hits.add(new VectorHit(
                                        (UUID) rs.getObject("id"),
                                        (UUID) rs.getObject("document_id"),
                                        rs.getInt("chunk_index"),
                                        rs.getString("content"),
                                        rs.getDouble("similarity"),
                                        rs.getString("file_path"),
                                        rs.getString("file_name")
                                ));
                            }
                            return hits;
                        }
                    }
                });
            }).subscribeOn(Schedulers.boundedElastic());
        }).onErrorResume(e -> {
            if (e instanceof ApiException api && "EMBEDDING_DIMENSIONS".equals(api.code())) return Mono.error(e);
            LOG.debug("Vector search error: {}", e.getMessage());
            return Mono.just(List.of());
        });
    }

    public Mono<List<VectorHit>> search(String query) {
        return search(query, 5, 0.5);
    }

    public void deleteDocument(String filePath) {
        if (!databaseStore.configured()) return;
        databaseStore.execute(c -> {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM jarvis.documents WHERE file_path = ? AND owner_name = session_user")) {
                s.setString(1, filePath);
                return s.executeUpdate();
            }
        });
    }

    private PreparedStatement documentCheck(Connection c, String filePath) throws SQLException {
        PreparedStatement s = c.prepareStatement(DOCUMENT_CHECK);
        s.setString(1, embeddingService.modelName());
        s.setString(2, Integer.toString(embeddingService.dimensions()));
        s.setString(3, filePath);
        return s;
    }

    private void requireSupportedDimensions() {
        if (embeddingService.dimensions() != 384) throw dimensionError();
    }

    private void validateVector(float[] vector) {
        if (vector.length != 384) throw dimensionError();
        for (float value : vector) if (!Float.isFinite(value)) throw dimensionError();
    }

    private ApiException dimensionError() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMBEDDING_DIMENSIONS",
            "The vector database requires 384 finite embedding values. Configure a compatible 384-dimensional embedding model and reindex your folders.");
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
