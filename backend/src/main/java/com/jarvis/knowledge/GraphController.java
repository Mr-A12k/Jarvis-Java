package com.jarvis.knowledge;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for the Obsidian-Style Neural Knowledge Graph.
 */
@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {

    private final NeuralGraphService graphService;

    @Autowired
    public GraphController(NeuralGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/topology")
    public NeuralGraphService.GraphTopology getTopology(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "150") int maxNodes) {
        return graphService.getTopology(filter, maxNodes);
    }
}
