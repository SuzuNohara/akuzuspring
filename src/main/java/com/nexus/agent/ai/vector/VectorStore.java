package com.nexus.agent.ai.vector;

import com.nexus.agent.ai.vector.filter.PayloadFilter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Puerto del almacén vectorial (nexus-AGENT-00). El dev implementa {@code QdrantVectorStore}
 * encima; en test se usa {@code InMemoryVectorStore}. La búsqueda es agrupada (un resultado por
 * {@code groupBy}, p. ej. por actividad) para no devolver varios lugares de la misma actividad.
 */
public interface VectorStore {

    void ensureCollection(CollectionSpec spec);

    void upsert(String collection, List<VectorPoint> points);

    void delete(String collection, Collection<String> ids);

    Map<String, String> payloadValues(String collection, String field);

    List<ScoredPoint> searchGrouped(
            String collectionOrAlias, float[] vector, PayloadFilter filter, String groupBy, int limit);

    void pointAlias(String alias, String collection);
}
