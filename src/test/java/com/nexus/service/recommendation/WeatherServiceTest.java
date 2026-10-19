package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexus.config.WeatherProperties;
import com.nexus.exception.WeatherUnavailableException;
import com.nexus.integration.weather.OpenMeteoClient;
import com.nexus.model.Scope;
import com.nexus.model.WeatherCondition;
import com.nexus.service.ProximityFilter.Coordinates;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Clima de la franja de una solicitud (CU40) y su respaldo. */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final Instant START = Instant.parse("2026-10-24T18:00:00Z");
    private static final Instant END = Instant.parse("2026-10-24T20:00:00Z");
    private static final Coordinates COYOACAN = new Coordinates(19.35, -99.16);

    @Mock private OpenMeteoClient client;

    private WeatherService service(boolean enabled) {
        return new WeatherService(client, new WeatherProperties(URI.create("http://meteo.test"), Duration.ofSeconds(3), enabled));
    }

    private static RecommendationQuery query(List<Coordinates> refs) {
        return new RecommendationQuery(Scope.ANY, 500, (com.nexus.model.IdeaCategory) null, START, END, refs);
    }

    @Test
    @DisplayName("should ask the forecast at the first reference of the query")
    void shouldAskTheForecastAtTheFirstReferenceOfTheQuery() {
        when(client.forecast(19.35, -99.16, START, END)).thenReturn(WeatherCondition.RAINY);

        assertThat(service(true).conditionFor(query(List.of(COYOACAN)))).isEqualTo(WeatherCondition.RAINY);
    }

    @Test
    @DisplayName("should use the center of mexico city without references")
    void shouldUseTheCenterOfMexicoCityWithoutReferences() {
        when(client.forecast(WeatherService.CDMX_CENTER.lat(), WeatherService.CDMX_CENTER.lon(), START, END))
                .thenReturn(WeatherCondition.SUNNY);

        assertThat(service(true).conditionFor(query(List.of()))).isEqualTo(WeatherCondition.SUNNY);
    }

    @Test
    @DisplayName("should be unknown when the service fails so nothing is filtered")
    void shouldBeUnknownWhenTheServiceFails() {
        when(client.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new WeatherUnavailableException("sin red"));

        assertThat(service(true).conditionFor(query(List.of(COYOACAN)))).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    @DisplayName("should not call the service when it is turned off")
    void shouldNotCallTheServiceWhenItIsTurnedOff() {
        assertThat(service(false).conditionFor(query(List.of(COYOACAN)))).isEqualTo(WeatherCondition.UNKNOWN);
        verifyNoInteractions(client);
    }
}
