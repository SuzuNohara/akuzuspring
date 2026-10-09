package com.nexus.agent.ai.compose;

/**
 * Cómo se produjo una composición (nexus-AGENT-00). Los {@code RULES_*} son respaldo por reglas
 * (D-08): el motor nunca devuelve error, cae a reglas con el modo trazado.
 */
public enum CompositionMode {
    LLM,
    LLM_FILLED,
    RULES_TIMEOUT,
    RULES_UNAVAILABLE,
    RULES_INVALID_OUTPUT,
    RULES_NO_BUDGET,
    NO_CANDIDATES;

    public boolean isFallback() {
        return name().startsWith("RULES_");
    }
}
