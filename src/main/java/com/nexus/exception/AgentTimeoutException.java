package com.nexus.exception;

/**
 * [t06b-agente-java] U10 - Ollama no respondio dentro del tiempo de la llamada. Es un caso de
 * {@link AgentUnavailableException} que el compositor distingue para reportar {@code RULES_TIMEOUT}.
 */
public class AgentTimeoutException extends AgentUnavailableException {

    public AgentTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
