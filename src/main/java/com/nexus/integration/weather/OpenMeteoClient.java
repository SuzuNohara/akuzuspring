package com.nexus.integration.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.config.WeatherProperties;
import com.nexus.exception.WeatherUnavailableException;
import com.nexus.model.WeatherCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del pronostico por hora de Open-Meteo (seccion "Informacion meteorologica"): codigo de
 * tiempo WMO y probabilidad de lluvia de hasta 16 dias.
 *
 * <p>Cualquier falla se reporta como {@link WeatherUnavailableException}; quien llama decide.
 */
public class OpenMeteoClient {

    private static final long HOUR_SECONDS = 3600;
    private static final int FORECAST_DAYS = 16;

    private final RestClient http;

    public OpenMeteoClient(RestClient.Builder builder, WeatherProperties properties) {
        this.http = builder.baseUrl(properties.baseUrl().toString()).build();
    }

    /**
     * Condicion de la franja en un punto: la peor de las horas que la tocan; {@link
     * WeatherCondition#UNKNOWN} si el pronostico no llega a esa franja.
     */
    public WeatherCondition forecast(double lat, double lon, Instant start, Instant end) {
        JsonNode hourly = fetch(lat, lon).path("hourly");
        JsonNode times = hourly.path("time");
        JsonNode codes = hourly.path("weather_code");
        JsonNode rain = hourly.path("precipitation_probability");
        if (!times.isArray() || !codes.isArray() || codes.size() != times.size()) {
            throw new WeatherUnavailableException("Open-Meteo no devolvio el pronostico por hora");
        }
        List<WeatherCondition> hours = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            long hourStart = times.get(i).asLong();
            boolean overlaps = hourStart < end.getEpochSecond() && hourStart + HOUR_SECONDS > start.getEpochSecond();
            if (overlaps && !codes.get(i).isNull()) {
                hours.add(WeatherCondition.ofHour(codes.get(i).asInt(), rain.path(i).asInt(0)));
            }
        }
        return WeatherCondition.worst(hours);
    }

    private JsonNode fetch(double lat, double lon) {
        try {
            JsonNode body = http.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", format(lat))
                            .queryParam("longitude", format(lon))
                            .queryParam("hourly", "weather_code,precipitation_probability")
                            .queryParam("timeformat", "unixtime")
                            .queryParam("timezone", "UTC")
                            .queryParam("forecast_days", FORECAST_DAYS)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new WeatherUnavailableException("Open-Meteo respondio vacio");
            }
            return body;
        } catch (RestClientException e) {
            throw new WeatherUnavailableException("Open-Meteo no respondio: " + e.getMessage(), e);
        }
    }

    /**
     * Dos decimales (~1 km): el pronostico no distingue menos que eso y asi la ubicacion de la
     * persona nunca sale exacta a un tercero (RN-30).
     */
    private static String format(double degrees) {
        return String.format(Locale.ROOT, "%.2f", degrees);
    }
}
