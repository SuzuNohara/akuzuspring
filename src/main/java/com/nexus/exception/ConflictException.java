package com.nexus.exception;

/**
 * La operacion choca con el estado actual del recurso (409): p. ej. ya no quedan opciones guardadas
 * o la solicitud ya se convirtio en invitacion.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
