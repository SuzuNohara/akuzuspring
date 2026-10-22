package com.nexus.integration.qdrant;

import java.util.Map;

/** [t06b-agente-java] U3 - Resultado de una busqueda agrupada: id del punto, similitud y atributos. */
public record QdrantHit(String id, double score, Map<String, Object> payload) {}
