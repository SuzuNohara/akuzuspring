package com.nexus.agent.ai.vector;

import java.util.List;

/** Especificación de una colección vectorial: nombre, dimensión y sus índices de payload. */
public record CollectionSpec(String name, int dimension, List<PayloadIndex> indexes) {

    public CollectionSpec {
        indexes = List.copyOf(indexes);
    }
}
