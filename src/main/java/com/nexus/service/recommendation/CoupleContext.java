package com.nexus.service.recommendation;

import com.nexus.model.IdeaCategory;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lo que se sabe de la pareja para ordenar opciones: cuantas preferencias eligieron entre los dos en
 * cada dimension del bienestar y el nivel combinado del animo de los dos en los ultimos 7 dias
 * (RN-28: nunca el de cada persona). Nunca sale en la respuesta.
 */
public record CoupleContext(Map<IdeaCategory, Integer> dimensionWeights, MoodLevel coupleValence, MoodLevel coupleEnergy) {

    public CoupleContext {
        coupleValence = coupleValence == null ? MoodLevel.SIN_DATO : coupleValence;
        coupleEnergy = coupleEnergy == null ? MoodLevel.SIN_DATO : coupleEnergy;
        EnumMap<IdeaCategory, Integer> copy = new EnumMap<>(IdeaCategory.class);
        copy.putAll(dimensionWeights);
        dimensionWeights = Collections.unmodifiableMap(copy);
    }

    /** Solo preferencias, sin datos de animo. */
    public CoupleContext(Map<IdeaCategory, Integer> dimensionWeights) {
        this(dimensionWeights, MoodLevel.SIN_DATO, MoodLevel.SIN_DATO);
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
