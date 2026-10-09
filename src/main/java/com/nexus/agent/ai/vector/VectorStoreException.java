package com.nexus.agent.ai.vector;

/** Fallo del almacén vectorial (nexus-AGENT-00). */
public final class VectorStoreException extends RuntimeException {

    public VectorStoreException(String message) {
        super(message);
    }

    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
