package com.nexus.exception;

/**
 * [T-06] Ollama o Qdrant no respondieron, respondieron con error o excedieron el tiempo limite.
 * Quien la recibe decide el respaldo: texto del catalogo (CU40 FA02) o seleccion en MySQL.
 */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }

    public AgentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
