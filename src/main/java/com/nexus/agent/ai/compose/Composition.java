package com.nexus.agent.ai.compose;

import java.util.List;

/**
 * Salida del compositor (nexus-AGENT-00): las recomendaciones (≤ 5, siempre ⊆ candidatas) y su
 * traza. La orquestación de CU40 decide qué mostrar a partir de esto.
 */
public record Composition(List<RecommendationDraft> drafts, CompositionTrace trace) {

    public Composition {
        drafts = List.copyOf(drafts);
    }
}
