package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Prueba de R7: sin {@code jwt.secret} el contexto debe abortar, no arrancar con un secreto vacio.
 *
 * <p>Sustituye de forma automatizada a la verificacion manual T-14, que en este entorno no puede
 * atribuirse a {@code jwt.secret}: sin el {@code .env} completo, {@code emailService} revienta antes
 * por {@code spring.mail.username}. Aqui se aisla el enlace de {@link JwtProperties}, sin base de
 * datos ni correo.
 */
class JwtPropertiesTest {

    /** 48 bytes UTF-8, por encima del minimo de HS256. Literal de prueba, no una credencial. */
    private static final String VALID_SECRET = "test-secret-para-jwtpropertiestest-00000000000A";

    /** Los mismos valores que enlaza el runner, para construir el record a mano. */
    private static final long EXPIRATION_MILLIS = 86_400_000L;

    private static final String ISSUER = "nexus-api";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(JwtPropertiesEnabled.class)
                    .withPropertyValues("jwt.expiration=86400000", "jwt.issuer=nexus-api");

    @Test
    @DisplayName("should fail context startup when jwt secret is missing")
    void shouldFailContextStartupWhenJwtSecretIsMissing() {
        // Sin las variables del sistema: un JWT_SECRET definido en el equipo haria arrancar el
        // contexto y la prueba dependeria de la maquina donde corre.
        runner.withInitializer(
                        context ->
                                context.getEnvironment()
                                        .getPropertySources()
                                        .remove(
                                                org.springframework.core.env.StandardEnvironment
                                                        .SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("should fail context startup when jwt secret is blank")
    void shouldFailContextStartupWhenJwtSecretIsBlank() {
        runner.withPropertyValues("jwt.secret=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("should bind properties when jwt secret is present")
    void shouldBindPropertiesWhenJwtSecretIsPresent() {
        runner.withPropertyValues("jwt.secret=" + VALID_SECRET)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(JwtProperties.class).issuer())
                                    .isEqualTo("nexus-api");
                        });
    }

    @Test
    @DisplayName("should mask the secret but keep issuer when properties are printed")
    void shouldMaskTheSecretButKeepIssuerWhenPropertiesArePrinted() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, EXPIRATION_MILLIS, ISSUER);

        String printed = properties.toString();

        assertThat(printed)
                .as("el toString autogenerado de un record imprimiria el secreto en claro")
                .doesNotContain(VALID_SECRET);
        assertThat(printed)
                .as("el emisor es publico y sirve para diagnosticar")
                .contains(ISSUER);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtPropertiesEnabled {}
}
