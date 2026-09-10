package com.nexus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origenes de navegador autorizados a llamar a la API.
 *
 * <p>La lista se declara en {@code app.cors.allowed-origins} y es la unica fuente de verdad de la
 * politica CORS. Solo admite origenes exactos: cualquier entrada con un comodin rompe el arranque
 * de la aplicacion, para que nadie pueda reabrir el reflejo de origen sin darse cuenta.
 *
 * @param allowedOrigins origenes exactos autorizados; vacia significa "ningun navegador"
 * @implNote Los clientes nativos (Expo Go, APK) no envian cabecera {@code Origin} y por tanto no se
 *     ven afectados por esta lista. Validacion O(n) sobre el numero de origenes configurados.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    private static final String WILDCARD = "*";

    /**
     * Normaliza la lista a una copia inmutable y rechaza cualquier comodin.
     *
     * @throws IllegalArgumentException si algun origen contiene {@code *}
     */
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        allowedOrigins.stream()
                .filter(origin -> origin.contains(WILDCARD))
                .findFirst()
                .ifPresent(
                        origin -> {
                            throw new IllegalArgumentException(
                                    "app.cors.allowed-origins no admite comodines; se recibio: "
                                            + origin);
                        });
    }
}
