package com.nexus.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pronostico del clima para las recomendaciones (CU40), prefijo {@code nexus.weather}.
 *
 * @param baseUrl direccion de la API de Open-Meteo (gratis, sin llave)
 * @param timeout tiempo maximo de la consulta; si se excede se recomienda sin filtrar por clima
 * @param enabled si se consulta el clima; apagado, nunca se descarta nada por clima
 */
@Validated
@ConfigurationProperties(prefix = "nexus.weather")
public record WeatherProperties(@NotNull URI baseUrl, @NotNull Duration timeout, boolean enabled) {}
