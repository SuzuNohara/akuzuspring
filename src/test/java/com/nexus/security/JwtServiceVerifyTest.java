package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verificacion del par emitir/verificar de {@link JwtService} (nexus-AUTH-02, R1/R2).
 *
 * <p>No se mockea nada: se emite un token de verdad con {@code issueFor} y se verifica de verdad.
 * Los casos degenerados (emisor ajeno, caducado, {@code sub} no numerico, {@code alg: none}) se
 * construyen a mano con el builder de jjwt, que {@code issueFor} no puede producir.
 */
class JwtServiceVerifyTest {

    /** Secreto literal de 48 bytes, mismo patron que nexus-AUTH-01. */
    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final String OTHER_SECRET =
            "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz0";

    private static final String ISSUER = "nexus-api";

    private final JwtService service =
            new JwtService(new JwtProperties(SECRET, 86_400_000L, ISSUER));

    private final SecretKey goodKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("should return principal with id and email when token is valid")
    void shouldReturnPrincipalWithIdAndEmailWhenTokenIsValid() {
        String token = service.issueFor(7L, "a@b.c").token();

        assertThat(service.verify(token)).contains(new AuthenticatedUser(7L, "a@b.c"));
    }

    @Test
    @DisplayName("should return empty when signature is forged")
    void shouldReturnEmptyWhenSignatureIsForged() {
        String token =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject("7")
                        .claim("email", "a@b.c")
                        .signWith(otherKey, Jwts.SIG.HS256)
                        .compact();

        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("should return empty when issuer is not nexus api")
    void shouldReturnEmptyWhenIssuerIsNotNexusApi() {
        String token =
                Jwts.builder()
                        .issuer("otro")
                        .subject("7")
                        .claim("email", "a@b.c")
                        .signWith(goodKey, Jwts.SIG.HS256)
                        .compact();

        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("should return empty when token is expired")
    void shouldReturnEmptyWhenTokenIsExpired() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String token =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject("7")
                        .claim("email", "a@b.c")
                        .issuedAt(Date.from(past))
                        .expiration(Date.from(past.plus(1, ChronoUnit.HOURS)))
                        .signWith(goodKey, Jwts.SIG.HS256)
                        .compact();

        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("should return empty when subject is not a number")
    void shouldReturnEmptyWhenSubjectIsNotANumber() {
        String token =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject("siete")
                        .claim("email", "a@b.c")
                        .signWith(goodKey, Jwts.SIG.HS256)
                        .compact();

        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("should return empty when token is not a jws")
    void shouldReturnEmptyWhenTokenIsNotAJws() {
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify("abc")).isEmpty();
        assertThat(service.verify("a.b")).isEmpty();
        assertThat(service.verify(null)).isEmpty();
    }

    @Test
    @DisplayName("should return empty when algorithm is none")
    void shouldReturnEmptyWhenAlgorithmIsNone() {
        String token =
                Jwts.builder().issuer(ISSUER).subject("7").claim("email", "a@b.c").compact();

        assertThat(service.verify(token)).isEmpty();
    }
}
