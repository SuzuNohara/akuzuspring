package com.nexus.service.recommendation.agent;

/**
 * [t06b-agente-java] U12 - Lo que paso en una composicion (los 13 campos del lab). Solo numeros y
 * valores cerrados: se puede escribir en el log sin datos personales (R4).
 *
 * <p>{@code llmMs}, {@code promptTokens}, {@code evalTokens}, {@code dropped} y {@code replaced} se
 * suman sobre los intentos.
 *
 * @param attempts llamadas a Mistral (0 si no se llamo)
 * @param doneReason el del ultimo intento; vacio si no hubo
 */
public record CompositionTrace(
        ComposeMode mode,
        int attempts,
        String promptVersion,
        String promptLevel,
        String model,
        String timePolicy,
        long llmMs,
        long overBudgetMs,
        int promptTokens,
        int evalTokens,
        int dropped,
        int replaced,
        String doneReason) {

    /** {@code mode=LLM attempts=1 ...} para el log. */
    public String toLogLine() {
        return "mode=" + mode + " attempts=" + attempts + " prompt_version=" + promptVersion
                + " prompt_level=" + promptLevel + " model=" + model + " time_policy=" + timePolicy
                + " llm_ms=" + llmMs + " over_budget_ms=" + overBudgetMs + " prompt_tokens=" + promptTokens
                + " eval_tokens=" + evalTokens + " dropped=" + dropped + " replaced=" + replaced
                + " done_reason=" + doneReason;
    }
}
