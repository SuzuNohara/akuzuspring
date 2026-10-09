package com.nexus.agent.ai.llm;

/** Fallo del modelo de lenguaje (nexus-AGENT-00). Base de las excepciones de {@link LlmClient}. */
public abstract class LlmException extends RuntimeException {

    protected LlmException(String message) {
        super(message);
    }

    protected LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
