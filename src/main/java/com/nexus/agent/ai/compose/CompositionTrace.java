package com.nexus.agent.ai.compose;

/**
 * Traza de una composición (nexus-AGENT-00, R39): modo, intentos, versión del prompt, modelo,
 * milisegundos de LLM, tokens y cuántas candidatas se descartaron/reemplazaron. {@link #toLogLine()}
 * se registra a INFO y <b>nunca</b> incluye nombres de preferencias, datos de usuario ni texto de
 * candidatas.
 */
public record CompositionTrace(
        CompositionMode mode,
        int attempts,
        String promptVersion,
        String model,
        long llmMillis,
        int promptTokens,
        int evalTokens,
        int dropped,
        int replaced) {

    public String toLogLine() {
        return "mode=" + mode
                + " attempts=" + attempts
                + " promptVersion=" + promptVersion
                + " model=" + model
                + " llmMs=" + llmMillis
                + " promptTokens=" + promptTokens
                + " evalTokens=" + evalTokens
                + " dropped=" + dropped
                + " replaced=" + replaced;
    }
}
