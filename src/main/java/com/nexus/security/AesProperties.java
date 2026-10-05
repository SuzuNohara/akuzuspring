package com.nexus.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion de cifrado simetrico (RNF-11), enlazada al prefijo {@code aes}.
 *
 * <p>{@code secret} no se declara en ningun fichero versionado: llega por la variable de entorno
 * {@code AES_SECRET}, igual que {@code jwt.secret} llega por {@code JWT_SECRET} (ver
 * {@link JwtProperties}). {@link AesEncryptionService} lo pasa por SHA-256 para derivar siempre
 * una llave de 32 bytes exactos (lo que AES-256 exige), sin importar la longitud del secreto
 * original -- por eso esta clase no valida una longitud minima en bytes como {@code JwtProperties}.
 *
 * @param secret passphrase de la que se deriva la llave de cifrado. Nunca se registra ni se
 *     devuelve.
 */
@Validated
@ConfigurationProperties(prefix = "aes")
public record AesProperties(@NotBlank String secret) {

    private static final String MASKED_SECRET = "****";

    @Override
    public String toString() {
        return "AesProperties[secret=" + MASKED_SECRET + "]";
    }
}
