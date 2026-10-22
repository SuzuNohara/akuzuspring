package com.nexus.service.recommendation.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * [t06b-agente-java] U8 - Esquema JSON de la respuesta de Mistral, que Ollama recibe como
 * {@code format} y convierte en gramatica: el modelo no puede devolver un {@code ref} fuera de la
 * lista ni un id o un titulo.
 *
 * <p>El {@code maxLength} de {@code why} es holgado a proposito: Ollama corta la cadena a media
 * palabra en el limite. El limite real (140) lo pone {@link PickValidator}.
 */
public final class OutputSchema {

    /** Largo minimo de la frase, igual en el esquema y en el validador. */
    public static final int WHY_MIN_CHARS = 20;

    /** Largo maximo de la frase en el esquema (el validador acepta hasta 140). */
    public static final int WHY_SCHEMA_MAX_CHARS = 200;

    private OutputSchema() {}

    /**
     * Esquema de {@code picks} con 1 a {@code min(maxPicks, n)} elecciones y {@code ref} de 1 a n.
     *
     * @param n numero de candidatas que se muestran a Mistral
     * @param maxPicks cuantas puede elegir (k)
     * @throws IllegalArgumentException si {@code n} o {@code maxPicks} es menor que 1
     * @implNote O(n) por el enum.
     */
    public static Map<String, Object> of(int n, int maxPicks) {
        if (n < 1 || maxPicks < 1) {
            throw new IllegalArgumentException("n y maxPicks deben ser >= 1: n=" + n + ", maxPicks=" + maxPicks);
        }
        Map<String, Object> why =
                Map.of("type", "string", "minLength", WHY_MIN_CHARS, "maxLength", WHY_SCHEMA_MAX_CHARS);
        Map<String, Object> ref =
                Map.of("type", "integer", "enum", IntStream.rangeClosed(1, n).boxed().toList());
        Map<String, Object> item = Map.of(
                "type", "object",
                "required", List.of("ref", "why"),
                "additionalProperties", false,
                "properties", Map.of("ref", ref, "why", why));
        Map<String, Object> picks =
                Map.of("type", "array", "minItems", 1, "maxItems", Math.min(maxPicks, n), "items", item);
        return Map.of(
                "type", "object",
                "required", List.of("picks"),
                "additionalProperties", false,
                "properties", Map.of("picks", picks));
    }
}
