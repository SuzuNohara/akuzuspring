package com.nexus.service.recommendation;

import com.nexus.config.WeatherProperties;
import com.nexus.exception.WeatherUnavailableException;
import com.nexus.integration.weather.OpenMeteoClient;
import com.nexus.model.WeatherCondition;
import com.nexus.service.ProximityFilter.Coordinates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CU40 - Clima previsto en la franja de una solicitud, en el punto de la cita: la primera
 * referencia (ubicacion o centro de la alcaldia) o, sin referencias, el centro de la CDMX.
 *
 * <p>Si el servicio falla o esta apagado devuelve {@link WeatherCondition#UNKNOWN}: el clima nunca
 * hace fallar ni vacia una recomendacion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    /** Zocalo de la CDMX. */
    static final Coordinates CDMX_CENTER = new Coordinates(19.4326, -99.1332);

    private final OpenMeteoClient client;
    private final WeatherProperties properties;

    public WeatherCondition conditionFor(RecommendationQuery query) {
        if (!properties.enabled()) {
            return WeatherCondition.UNKNOWN;
        }
        Coordinates point = query.references().isEmpty() ? CDMX_CENTER : query.references().get(0);
        try {
            return client.forecast(point.lat(), point.lon(), query.slotStart(), query.slotEnd());
        } catch (WeatherUnavailableException e) {
            log.warn("Clima no disponible, se recomienda sin filtrar por clima: {}", e.getMessage());
            return WeatherCondition.UNKNOWN;
        }
    }
}
