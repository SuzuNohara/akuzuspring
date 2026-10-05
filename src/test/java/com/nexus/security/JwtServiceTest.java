package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link JwtService}. Sin contexto de Spring: el servicio recibe un
 * {@link JwtProperties} construido a mano.
 *
 * <p>Los secretos de esta clase son literales de prueba, no credenciales: nunca han firmado un
 * token real y no coinciden con ningun valor de entorno.
 */
class JwtServiceTest {

    /** Secreto de prueba, 48 caracteres ASCII = 48 bytes UTF-8, por encima del minimo de 32. */
    private static final String TEST_SECRET = "test-secret-para-jwtservicetest-000000000000000A";

    /** Segundo secreto de prueba, distinto del primero, para el caso de firma ajena. */
    private static final String OTHER_SECRET = "otro-secreto-de-prueba-distinto-00000000000000B0";

    /**
     * Tercer secreto de prueba, en el papel del secreto retirado por la rotacion de
     * {@code nexus-SEC-01}. No es el valor rotado: es un literal de prueba con la misma forma.
     */
    private static final String ROTATED_OUT_SECRET = "secreto-retirado-por-la-rotacion-de-sec-01-00000";

    /** Emisor configurado, el mismo que {@code jwt.issuer} en produccion. */
    private static final String TEST_ISSUER = "nexus-api";

    /** Vida del token en milisegundos, el mismo valor que {@code jwt.expiration}. */
    private static final long TTL_MILLIS = 86_400_000L;

    /** La misma vida expresada en segundos, que es la unidad de {@code iat} y {@code exp}. */
    private static final long TTL_SECONDS = 86_400L;

    /** Minimo de HS256 en bytes; la frontera que vigila el caso de secreto debil. */
    private static final int MIN_SECRET_BYTES = 32;

    /** 31 bytes UTF-8: un byte por debajo de la frontera. */
    private static final String WEAK_SECRET = "secreto-debil-que-no-debe-salir";

    /** 32 bytes UTF-8: justo en la frontera, debe construir. */
    private static final String MINIMAL_SECRET = "secreto-debil-que-no-debe-salir!";

    private static final long USER_ID = 42L;

    private static final String USER_EMAIL = "a@b.c";

    @Test
    @DisplayName("should set subject to the decimal user id when token is issued")
    void shouldSetSubjectToTheDecimalUserIdWhenTokenIsIssued() {
        JwtService service = new JwtService(properties(TEST_SECRET));

        IssuedToken issued = service.issueFor(USER_ID, USER_EMAIL);

        assertThat(claimsOf(issued.token(), TEST_SECRET).getSubject()).isEqualTo("42");
    }

    @Test
    @DisplayName("should contain only the agreed claim set when token is issued")
    void shouldContainOnlyTheAgreedClaimSetWhenTokenIsIssued() {
        JwtService service = new JwtService(properties(TEST_SECRET));

        IssuedToken issued = service.issueFor(USER_ID, USER_EMAIL);

        assertThat(claimsOf(issued.token(), TEST_SECRET))
                .containsOnlyKeys("iss", "sub", "email", "iat", "exp")
                .containsEntry("email", USER_EMAIL);
    }

    @Test
    @DisplayName("should set issuer to the configured value when token is issued")
    void shouldSetIssuerToTheConfiguredValueWhenTokenIsIssued() {
        JwtService service = new JwtService(properties(TEST_SECRET));

        IssuedToken issued = service.issueFor(USER_ID, USER_EMAIL);

        assertThat(claimsOf(issued.token(), TEST_SECRET).getIssuer()).isEqualTo(TEST_ISSUER);
    }

    @Test
    @DisplayName("should set expiration exactly one ttl after issued at when token is issued")
    void shouldSetExpirationExactlyOneTtlAfterIssuedAtWhenTokenIsIssued() {
        JwtService service = new JwtService(properties(TEST_SECRET));

        IssuedToken issued = service.issueFor(USER_ID, USER_EMAIL);

        Claims claims = claimsOf(issued.token(), TEST_SECRET);
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Instant expiration = claims.getExpiration().toInstant();
        assertThat(Duration.between(issuedAt, expiration)).isEqualTo(Duration.ofSeconds(TTL_SECONDS));
        assertThat(issued.expiresAt()).isEqualTo(expiration);
    }

    @Test
    @DisplayName("should declare HS256 in the token header when token is issued")
    void shouldDeclareHs256InTheTokenHeaderWhenTokenIsIssued() {
        JwtService service = new JwtService(properties(TEST_SECRET));

        IssuedToken issued = service.issueFor(USER_ID, USER_EMAIL);

        assertThat(decodedHeaderOf(issued.token()))
                .as("sin el algoritmo explicito, jjwt lo infiere del tamano de la clave")
                .contains("\"alg\":\"HS256\"");
    }

    @Test
    @DisplayName("should reject token when verified with a different secret when token is issued")
    void shouldRejectTokenWhenVerifiedWithADifferentSecretWhenTokenIsIssued() {
        JwtService service = new JwtService(properties(TEST_SECRET));

        IssuedToken issued = service.issueFor(USER_ID, USER_EMAIL);

        assertThatThrownBy(() -> claimsOf(issued.token(), OTHER_SECRET))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("should reject secret shorter than 32 bytes when service is constructed")
    void shouldRejectSecretShorterThan32BytesWhenServiceIsConstructed() {
        assertThat(WEAK_SECRET.getBytes(StandardCharsets.UTF_8)).hasSize(MIN_SECRET_BYTES - 1);
        assertThat(MINIMAL_SECRET.getBytes(StandardCharsets.UTF_8)).hasSize(MIN_SECRET_BYTES);

        assertThatThrownBy(() -> new JwtService(properties(WEAK_SECRET)))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new JwtService(properties(MINIMAL_SECRET))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should not leak secret value in construction error when secret is weak")
    void shouldNotLeakSecretValueInConstructionErrorWhenSecretIsWeak() {
        assertThatThrownBy(() -> new JwtService(properties(WEAK_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(WEAK_SECRET))
                .hasMessageContaining("jwt.secret")
                .hasMessageContaining(String.valueOf(MIN_SECRET_BYTES));
    }

    @Test
    @DisplayName("should reject token signed with the rotated out secret when verified with the current key")
    void shouldRejectTokenSignedWithTheRotatedOutSecretWhenVerifiedWithTheCurrentKey() {
        JwtService beforeRotation = new JwtService(properties(ROTATED_OUT_SECRET));
        IssuedToken issuedBeforeRotation = beforeRotation.issueFor(USER_ID, USER_EMAIL);

        assertThatThrownBy(() -> claimsOf(issuedBeforeRotation.token(), TEST_SECRET))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("should return the user id and email encoded in a valid token when parsed")
    void shouldReturnTheUserIdAndEmailEncodedInAValidTokenWhenParsed() {
        JwtService service = new JwtService(properties(TEST_SECRET));
        IssuedToken issued = service.issueFor(USER_ID, USER_EMAIL);

        AuthenticatedUser parsed = service.parse(issued.token());

        assertThat(parsed.userId()).isEqualTo(USER_ID);
        assertThat(parsed.email()).isEqualTo(USER_EMAIL);
    }

    @Test
    @DisplayName("should reject an expired token when parsed")
    void shouldRejectAnExpiredTokenWhenParsed() {
        JwtService service = new JwtService(properties(TEST_SECRET));
        String expired = expiredTokenFor(TEST_SECRET);

        assertThatThrownBy(() -> service.parse(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("should reject a token signed with a different secret when parsed")
    void shouldRejectATokenSignedWithADifferentSecretWhenParsed() {
        JwtService issuer = new JwtService(properties(OTHER_SECRET));
        JwtService verifier = new JwtService(properties(TEST_SECRET));
        IssuedToken issued = issuer.issueFor(USER_ID, USER_EMAIL);

        assertThatThrownBy(() -> verifier.parse(issued.token()))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("should reject a malformed token when parsed")
    void shouldRejectAMalformedTokenWhenParsed() {
        JwtService service = new JwtService(properties(TEST_SECRET));

        assertThatThrownBy(() -> service.parse("esto-no-es-un-jwt"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("should reject a token with an unexpected issuer when parsed")
    void shouldRejectATokenWithAnUnexpectedIssuerWhenParsed() {
        JwtService service = new JwtService(properties(TEST_SECRET));
        String wrongIssuer = tokenWithIssuer(TEST_SECRET, "otro-emisor");

        assertThatThrownBy(() -> service.parse(wrongIssuer))
                .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, TTL_MILLIS, TEST_ISSUER);
    }

    /** Token ya caducado, construido a mano porque {@code issueFor} siempre usa el TTL vigente. */
    private static String expiredTokenFor(String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant issuedAt = Instant.now().minusSeconds(120);
        Instant expiration = Instant.now().minusSeconds(60);
        return Jwts.builder()
                .issuer(TEST_ISSUER)
                .subject(Long.toString(USER_ID))
                .claim("email", USER_EMAIL)
                .issuedAt(java.util.Date.from(issuedAt))
                .expiration(java.util.Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Token valido en todo menos el emisor, para probar que {@code parse} tambien lo exige. */
    private static String tokenWithIssuer(String secret, String issuer) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plusSeconds(TTL_SECONDS);
        return Jwts.builder()
                .issuer(issuer)
                .subject(Long.toString(USER_ID))
                .claim("email", USER_EMAIL)
                .issuedAt(java.util.Date.from(issuedAt))
                .expiration(java.util.Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Decodifica en claro la cabecera del JWS sin pasar por el parser, que aceptaria cualquier
     * algoritmo HMAC y borraria la diferencia entre HS256 y HS512.
     *
     * @implNote O(n) sobre la longitud del primer segmento.
     */
    private static String decodedHeaderOf(String token) {
        String[] segments = token.split("\\.");
        assertThat(segments).as("un JWS compacto tiene tres segmentos").hasSize(3);
        return new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);
    }

    private static Claims claimsOf(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
