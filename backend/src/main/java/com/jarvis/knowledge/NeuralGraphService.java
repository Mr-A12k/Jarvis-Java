package com.jarvis.knowledge;

import com.jarvis.coding.CodingDatabaseStore;
import com.jarvis.database.DatabaseStore;
import com.jarvis.files.LocalFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Obsidian-Style Neural Knowledge Graph Engine.
 * Gathers relational entities (Database Memories, Chats, RAG Documents,
 * Coding Sessions, Modified Files, and Learned Web Facts) and generates
 * a synaptic network of nodes and weighted edges for interactive visualization.
 */
@Service
public class NeuralGraphService {

    private static final Logger log = LoggerFactory.getLogger(NeuralGraphService.class);

    public record GraphTopology(
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            Map<String, Integer> categoryCounts
    ) {}

    private final DatabaseStore databaseStore;
    private final LocalFiles localFiles;
    private final CodingDatabaseStore codingDatabaseStore;
    private final KnowledgeService knowledgeService;

    @Autowired
    public NeuralGraphService(
            @Autowired(required = false) DatabaseStore databaseStore,
            @Autowired(required = false) LocalFiles localFiles,
            @Autowired(required = false) CodingDatabaseStore codingDatabaseStore,
            @Autowired(required = false) KnowledgeService knowledgeService) {
        this.databaseStore = databaseStore;
        this.localFiles = localFiles;
        this.codingDatabaseStore = codingDatabaseStore;
        this.knowledgeService = knowledgeService;
    }

    /**
     * Constructs the full neural graph topology across all system entities.
     */
    public GraphTopology getTopology(String filter, int maxNodes) {
        int limit = maxNodes > 0 ? Math.min(maxNodes, 300) : 150;
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Map<String, Set<String>> nodeKeywords = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        // 1. Ingest Database Memories (EMERALD)
        if (databaseStore != null && databaseStore.configured()) {
            try {
                var memories = databaseStore.list(DatabaseStore.Kind.memories, 40, 0);
                for (var mem : memories) {
                    String id = "mem-" + mem.get("id");
                    String title = String.valueOf(mem.getOrDefault("title", "Memory"));
                    String content = String.valueOf(mem.getOrDefault("content", ""));

                    nodes.add(new GraphNode(id, title, "MEMORY", 14, Map.of(
                            "content", content,
                            "type", "Persistent Memory",
                            "created_at", String.valueOf(mem.get("created_at"))
                    )));
                    nodeKeywords.put(id, extractKeywords(title + " " + content));
                    counts.merge("MEMORY", 1, Integer::sum);
                }
            } catch (Exception e) {
                log.debug("NeuralGraphService: Error reading memories: {}", e.getMessage());
            }
        }

        // 2. Ingest Coding Sessions & Modified Files (AMBER & BLUE)
        if (codingDatabaseStore != null) {
            try {
                var sessions = codingDatabaseStore.listSessions();
                for (var s : sessions) {
                    String sId = "code-" + s.id();
                    nodes.add(new GraphNode(sId, s.title(), "CODE_SESSION", 16, Map.of(
                            "objective", s.objective(),
                            "status", s.status(),
                            "model", s.model(),
                            "totalTurns", s.totalTurns(),
                            "filesModified", s.filesModified()
                    )));
                    nodeKeywords.put(sId, extractKeywords(s.title() + " " + s.objective()));
                    counts.merge("CODE_SESSION", 1, Integer::sum);

                    // Add touched files and link them
                    for (String file : s.filesModified()) {
                        String fileId = "file-" + file.replace('/', '-');
                        if (nodes.stream().noneMatch(n -> n.id().equals(fileId))) {
                            nodes.add(new GraphNode(fileId, file.substring(file.lastIndexOf('/') + 1), "CODE_FILE", 10, Map.of(
                                    "path", file,
                                    "type", "Workspace Source File"
                            )));
                            counts.merge("CODE_FILE", 1, Integer::sum);
                        }
                        edges.add(new GraphEdge(sId, fileId, "MODIFIES", 1.0));
                    }
                }
            } catch (Exception e) {
                log.debug("NeuralGraphService: Error reading coding sessions: {}", e.getMessage());
            }
        }

        // 3. Ingest Learned Facts (ROSE)
        if (knowledgeService != null) {
            try {
                var facts = knowledgeService.listRecent(25);
                for (var f : facts) {
                    String fId = "fact-" + f.id();
                    nodes.add(new GraphNode(fId, f.query(), "LEARNED_FACT", 12, Map.of(
                            "answer", f.answer(),
                            "sources", f.sources(),
                            "type", "Learned Web Grounding"
                    )));
                    nodeKeywords.put(fId, extractKeywords(f.query() + " " + f.answer()));
                    counts.merge("LEARNED_FACT", 1, Integer::sum);
                }
            } catch (Exception e) {
                log.debug("NeuralGraphService: Error reading learned facts: {}", e.getMessage());
            }
        }

        // 4. Ingest Local Documents (CYAN)
        if (localFiles != null) {
            try {
                var state = localFiles.state();
                if (state != null && state.documents() != null) {
                    for (var doc : state.documents()) {
                        String docName = doc.path();
                        String docId = "doc-" + docName.hashCode();
                        nodes.add(new GraphNode(docId, docName, "DOCUMENT", 12, Map.of(
                                "name", docName,
                                "type", "Uploaded Workspace Document"
                        )));
                        nodeKeywords.put(docId, extractKeywords(docName));
                        counts.merge("DOCUMENT", 1, Integer::sum);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 5. Compute Synaptic Similarity Edges
        computeSynapticEdges(nodes, nodeKeywords, edges);

        // Apply filter if specified
        if (filter != null && !filter.isBlank()) {
            String lowerFilter = filter.toLowerCase(Locale.ROOT);
            Set<String> matchedIds = new HashSet<>();
            for (GraphNode node : nodes) {
                if (node.label().toLowerCase(Locale.ROOT).contains(lowerFilter)
                        || node.type().toLowerCase(Locale.ROOT).contains(lowerFilter)) {
                    matchedIds.add(node.id());
                }
            }
            // Include 1-hop connected neighbors
            Set<String> connectedIds = new HashSet<>(matchedIds);
            for (GraphEdge edge : edges) {
                if (matchedIds.contains(edge.source())) connectedIds.add(edge.target());
                if (matchedIds.contains(edge.target())) connectedIds.add(edge.source());
            }
            nodes.removeIf(n -> !connectedIds.contains(n.id()));
            edges.removeIf(e -> !connectedIds.contains(e.source()) || !connectedIds.contains(e.target()));
        }

        // Enforce maximum node limit
        if (nodes.size() > limit) {
            nodes = nodes.subList(0, limit);
            Set<String> validIds = new HashSet<>(nodes.stream().map(GraphNode::id).toList());
            edges.removeIf(e -> !validIds.contains(e.source()) || !validIds.contains(e.target()));
        }

        return new GraphTopology(nodes, edges, counts);
    }

    private void computeSynapticEdges(List<GraphNode> nodes, Map<String, Set<String>> nodeKeywords, List<GraphEdge> edges) {
        Set<String> existingPairs = new HashSet<>();
        for (GraphEdge e : edges) {
            existingPairs.add(e.source() + "<->" + e.target());
            existingPairs.add(e.target() + "<->" + e.source());
        }

        for (int i = 0; i < nodes.size(); i++) {
            GraphNode n1 = nodes.get(i);
            Set<String> kw1 = nodeKeywords.getOrDefault(n1.id(), Collections.emptySet());
            if (kw1.isEmpty()) continue;

            for (int j = i + 1; j < nodes.size(); j++) {
                GraphNode n2 = nodes.get(j);
                String pairKey = n1.id() + "<->" + n2.id();
                if (existingPairs.contains(pairKey)) continue;

                Set<String> kw2 = nodeKeywords.getOrDefault(n2.id(), Collections.emptySet());
                if (kw2.isEmpty()) continue;

                // Calculate Jaccard similarity between keywords
                int intersection = 0;
                for (String w : kw1) {
                    if (kw2.contains(w)) intersection++;
                }

                if (intersection >= 2) {
                    double weight = Math.min(0.9, 0.4 + (intersection * 0.1));
                    edges.add(new GraphEdge(n1.id(), n2.id(), "SHARES_TOPIC", weight));
                    existingPairs.add(pairKey);
                }
            }
        }
    }

    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();
        Set<String> set = new HashSet<>();
        String[] words = text.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9_-]+");
        Set<String> stopWords = Set.of("the", "and", "for", "with", "that", "this", "from", "are", "have", "been", "was", "were", "what", "which", "will");
        for (String w : words) {
            if (w.length() >= 4 && !stopWords.contains(w)) {
                set.add(w);
            }
        }
        return set;
    }
}
