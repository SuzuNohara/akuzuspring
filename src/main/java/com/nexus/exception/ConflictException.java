package com.nexus.exception;

/**
 * Conflicto de estado: la operación no puede completarse porque otra ya ganó la carrera
 * (nexus-EMO-01, RN-38). El handler la traduce a HTTP 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
