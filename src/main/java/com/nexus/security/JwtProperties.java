package com.nexus.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion de emision de JWT, enlazada al prefijo {@code jwt}.
 *
 * <p>{@code secret} no se declara en ningun fichero versionado: llega por la variable de entorno
 * {@code JWT_SECRET} mediante el enlace relajado de Spring. {@link Validated} junto a
 * {@link NotBlank} convierte su ausencia en un fallo de arranque del contexto en vez de un
 * {@code null} silencioso que degradaria a tokens sin firma util.
 *
 * <p>No declara {@code refresh-expiration}: esa propiedad existe en el fichero y se deja
 * deliberadamente sin consumir.
 *
 * @param secret clave de firma HS256, en bytes UTF-8 de la cadena. Nunca se registra ni se
 *     devuelve.
 * @param expiration vida del token en milisegundos.
 * @param issuer valor del claim {@code iss}. Es publico, no un secreto.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret, @Positive long expiration, @NotBlank String issuer) {}
