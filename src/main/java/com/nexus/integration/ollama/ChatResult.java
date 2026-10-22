package com.nexus.integration.ollama;

/**
 * [t06b-agente-java] U10 - Respuesta del chat de Ollama, sin validar.
 *
 * @param content texto del asistente (JSON segun el esquema pedido)
 * @param doneReason por que termino ({@code stop}, o {@code length} si se acabaron los tokens)
 * @param promptTokens tokens del prompt ({@code prompt_eval_count})
 * @param evalTokens tokens generados ({@code eval_count})
 * @param wallMs tiempo de la llamada medido desde Java
 */
public record ChatResult(String content, String doneReason, int promptTokens, int evalTokens, long wallMs) {}
