package com.nexus.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emisor de los JWT de sesion.
 *
 * <p>El token lleva exactamente los claims {@code iss}, {@code sub}, {@code email}, {@code iat} y
 * {@code exp}. No lleva vinculo ni pareja a proposito: desvincular borra la fila, y un claim de
 * pareja acunado en el login seguiria autorizando el acceso a los datos del ex-partner durante toda
 * la vida del token.
 *
 * <p>Esta clase no declara logger a proposito: no puede filtrar el secreto a ningun log.
 */
@Service
public class JwtService {

    /** Minimo de HS256: 256 bits de clave. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;

    /**
     * @param properties configuracion de firma, validada por el contexto.
     * @throws IllegalStateException si el secreto no alcanza los {@value #MIN_SECRET_BYTES} bytes
     *     que HS256 exige. El mensaje nombra la propiedad y la longitud medida, nunca el valor.
     */
    public JwtService(JwtProperties properties) {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret debe medir al menos "
                            + MIN_SECRET_BYTES
                            + " bytes para HS256; mide "
                            + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttl = Duration.ofMillis(properties.expiration());
        this.issuer = properties.issuer();
    }

    /**
     * Emite un JWT HS256 para el usuario indicado.
     *
     * @param userId identificador del usuario, que viaja en el claim {@code sub} en decimal.
     * @param email correo del usuario, que viaja en el claim {@code email}.
     * @return el token compacto y su instante de caducidad.
     * @implNote O(1) en tiempo y espacio respecto del tamano del payload.
     */
    public IssuedToken issueFor(long userId, String email) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        String token =
                Jwts.builder()
                        .issuer(issuer)
                        .subject(Long.toString(userId))
                        .claim("email", email)
                        .issuedAt(Date.from(issuedAt))
                        .expiration(Date.from(expiresAt))
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();
        return new IssuedToken(token, expiresAt);
    }
}
