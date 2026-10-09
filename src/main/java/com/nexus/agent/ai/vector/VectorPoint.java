package com.nexus.agent.ai.vector;

import java.util.Map;

/** Un punto a indexar: id, vector y payload (nexus-AGENT-00). */
public record VectorPoint(String id, float[] vector, Map<String, Object> payload) {

    public VectorPoint {
        payload = Map.copyOf(payload);
    }
}
