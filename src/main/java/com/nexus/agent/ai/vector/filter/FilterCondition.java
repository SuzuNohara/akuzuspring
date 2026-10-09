package com.nexus.agent.ai.vector.filter;

import java.util.List;

/**
 * Condición de filtro sobre el payload, independiente del motor (nexus-AGENT-00). El mapeador a
 * Qdrant (dev) la traduce con un {@code switch} de patrones.
 */
public sealed interface FilterCondition {

    /** El campo es exactamente igual a un valor (o lo contiene, si el payload es lista). */
    record Match(String key, Object value) implements FilterCondition {}

    /** El campo coincide con alguno de los valores. */
    record MatchAny(String key, List<?> values) implements FilterCondition {
        public MatchAny {
            values = List.copyOf(values);
            if (values.isEmpty()) {
                throw new IllegalArgumentException("MatchAny sin valores: " + key);
            }
        }
    }

    /** Rango numérico; al menos un límite debe estar presente. */
    record Range(String key, Double gte, Double lte) implements FilterCondition {
        public Range {
            if (gte == null && lte == null) {
                throw new IllegalArgumentException("Range sin límites: " + key);
            }
        }
    }

    /** El punto está dentro de un radio en metros de un centro geográfico. */
    record GeoRadius(String key, double lat, double lon, double radiusMeters)
            implements FilterCondition {}

    /** El campo está ausente. */
    record IsNull(String key) implements FilterCondition {}

    /** Al menos una de las condiciones se cumple (OR). */
    record AnyOf(List<FilterCondition> options) implements FilterCondition {
        public AnyOf {
            options = List.copyOf(options);
        }
    }
}
