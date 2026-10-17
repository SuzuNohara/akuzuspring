package com.nexus.dto;

import java.util.List;
import java.util.Set;

/**
 * Las opciones que se muestran ahora (hasta 5). Nunca dice cuantas quedan: solo si hay mas.
 *
 * @param relaxedFilters filtros ampliados para juntar opciones (CATEGORY, DAYPART, BUDGET, SCOPE)
 * @param selectionSource {@code CATALOG} o {@code VECTOR}
 * @param hasMore si "Buscar mas opciones" todavia trae algo sin pedir otra busqueda
 */
public record RecommendationBatch(
        Long requestId,
        List<RecommendationItem> items,
        Set<String> relaxedFilters,
        String selectionSource,
        boolean hasMore) {}
