package com.nexus.integration.ollama;

/**
 * [t06b-agente-java] U10 - Opciones de generacion del chat.
 *
 * @param numCtx tamano del contexto en tokens ({@code num_ctx})
 * @param numPredict tope de tokens de la respuesta ({@code num_predict})
 * @param seed semilla para respuestas repetibles; {@code null} para no fijarla
 */
public record ChatOptions(double temperature, int numCtx, int numPredict, Integer seed) {}
