package com.nexus.service.recommendation.agent;

import java.util.List;

/**
 * [t06b-agente-java] U12 - La lista final, en orden: primero las de Mistral y luego las de las
 * reglas, cada una con su frase y su fuente.
 */
public record Composition(List<WrittenPick> picks, CompositionTrace trace) {

    public Composition {
        picks = List.copyOf(picks);
    }
}
