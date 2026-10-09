package com.nexus.agent.ai.llm;

/**
 * Respuesta del LLM (nexus-AGENT-00): el contenido y las métricas que alimentan la traza y el
 * benchmark (tokens de prompt/generación, duración de evaluación y tiempo de pared medido).
 */
public record LlmResponse(
        String content,
        int promptTokens,
        int evalTokens,
        long evalDurationNanos,
        long wallMillis) {}
