package com.nexus.security;

import java.time.Instant;

/**
 * Par token/caducidad devuelto por {@link JwtService#issueFor(long, String)}.
 *
 * <p>Devolver la caducidad junto al token evita que el llamante tenga que volver a decodificar el
 * JWT solo para leer su claim {@code exp}.
 *
 * @param token JWT compacto, firmado con HS256.
 * @param expiresAt instante de caducidad, exactamente el {@code exp} del token, truncado al
 *     segundo.
 */
public record IssuedToken(String token, Instant expiresAt) {}
