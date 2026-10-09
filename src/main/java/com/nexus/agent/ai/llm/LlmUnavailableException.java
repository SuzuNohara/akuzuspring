package com.nexus.agent.ai.llm;

/** El LLM no está disponible (caído, 5xx, conexión rechazada) (nexus-AGENT-00). */
public final class LlmUnavailableException extends LlmException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
