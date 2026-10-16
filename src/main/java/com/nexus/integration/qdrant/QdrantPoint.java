package com.nexus.integration.qdrant;

import java.util.Map;

/** [T-06] Una actividad del catalogo dentro de Qdrant: su id de MySQL, su vector y sus atributos. */
public record QdrantPoint(long id, float[] vector, Map<String, Object> payload) {}
