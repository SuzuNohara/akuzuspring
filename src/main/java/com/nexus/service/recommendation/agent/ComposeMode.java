package com.nexus.service.recommendation.agent;

/** [t06b-agente-java] U12 - Quien eligio las k primeras recomendaciones y por que (igual que el lab). */
public enum ComposeMode {
    /** Mistral eligio las k. */
    LLM,
    /** Mistral eligio menos de k validas; las reglas completaron. */
    LLM_FILLED,
    /** Mistral se paso del tiempo de la llamada. */
    RULES_TIMEOUT,
    /** Ollama caido o con error (incluye el 400 de un Ollama sin {@code format} con esquema, R7). */
    RULES_UNAVAILABLE,
    /** Ningun intento dio elecciones validas, o la lista final no paso la guardia. */
    RULES_INVALID_OUTPUT,
    /** No quedaba tiempo minimo para llamar a Mistral (politica HARD). */
    RULES_NO_BUDGET,
    /** No habia candidatas. */
    NO_CANDIDATES,
    /** Agente apagado: todo por reglas. */
    DISABLED
}
