package com.nexus.exception;

/**
 * El usuario autenticado existe y el token es valido, pero no es el dueno del recurso pedido (p.
 * ej. un {@code {userId}} de la URL que no coincide con el principal) ni tiene ningun otro
 * permiso sobre el (nexus-SEC-04). Se mapea a 403 en {@link GlobalExceptionHandler}.
 *
 * <p>Distinta de un token ausente/invalido, que nunca llega a un controller: la rechaza
 * {@code SecurityConfig} antes, con 401/403 por defecto de Spring Security.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
