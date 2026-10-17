package com.nexus.service.recommendation;

import com.nexus.model.IdeaCategory;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lo que se sabe de la pareja para ordenar opciones: cuantas preferencias eligieron entre los dos en
 * cada dimension del bienestar. El nivel combinado de animo (RN-28) se agrega al conectar el agente.
 */
public record CoupleContext(Map<IdeaCategory, Integer> dimensionWeights) {

    public CoupleContext {
        EnumMap<IdeaCategory, Integer> copy = new EnumMap<>(IdeaCategory.class);
        copy.putAll(dimensionWeights);
        dimensionWeights = Collections.unmodifiableMap(copy);
    }

    /** Sin preferencias conocidas: todas las dimensiones pesan igual. */
    public static CoupleContext empty() {
        return new CoupleContext(Map.of());
    }

    /** Peso de una dimension; 0 si ninguno eligio preferencias en ella. */
    public int weight(IdeaCategory category) {
        return dimensionWeights.getOrDefault(category, 0);
    }
}
