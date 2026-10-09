package com.nexus.agent.ai.vector;

import java.util.Map;

/** Un punto recuperado con su score de similitud (nexus-AGENT-00). */
public record ScoredPoint(String id, double score, Map<String, Object> payload) {

    public ScoredPoint {
        payload = Map.copyOf(payload);
    }
}
