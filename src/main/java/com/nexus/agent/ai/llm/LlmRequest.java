package com.nexus.agent.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Petición al LLM (nexus-AGENT-00): los mensajes, el esquema JSON que fuerza la salida estructurada
 * ({@code format}) y el presupuesto de tokens de generación.
 */
public record LlmRequest(List<ChatMessage> messages, JsonNode jsonSchema, int numPredict) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }
}
