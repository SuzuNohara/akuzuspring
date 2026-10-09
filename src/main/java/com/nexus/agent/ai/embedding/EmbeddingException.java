package com.nexus.agent.ai.embedding;

/** Fallo al calcular embeddings (nexus-AGENT-00). */
public final class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
