package com.nexus.security;

/**
 * Principal autenticado que {@link JwtAuthFilter} instala en el {@code SecurityContext} tras
 * validar el token.
 *
 * <p>Los controllers lo reciben via {@code @AuthenticationPrincipal} para derivar el usuario real
 * de la peticion, en vez de confiar en el {@code {userId}} que venga en la URL (nexus-SEC-04).
 *
 * @param userId identificador del usuario, tomado del claim {@code sub}.
 * @param email correo del usuario, tomado del claim {@code email}.
 */
public record AuthenticatedUser(long userId, String email) {}
