package com.nexus.integration.qdrant;

import java.util.Map;

/**
 * [T-06] Un punto de Qdrant: su id, su vector y sus atributos.
 *
 * @param id [t06b-agente-java] UUID de la oferta ({@code CanonicalText.pointId})
 */
public record QdrantPoint(String id, float[] vector, Map<String, Object> payload) {}
