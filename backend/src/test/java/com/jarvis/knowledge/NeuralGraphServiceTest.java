package com.jarvis.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NeuralGraphServiceTest {

    @Test
    void testTopologyGenerationWithEmptyOrFallbackServices() {
        NeuralGraphService service = new NeuralGraphService(null, null, null, null);
        var topology = service.getTopology("", 100);

        assertThat(topology).isNotNull();
        assertThat(topology.nodes()).isNotNull();
        assertThat(topology.edges()).isNotNull();
    }
}
