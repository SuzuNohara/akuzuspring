package com.nexus.agent.ai.retrieval;

/** Fallo al recuperar candidatas del índice (nexus-AGENT-00). */
public final class RetrievalException extends RuntimeException {

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
