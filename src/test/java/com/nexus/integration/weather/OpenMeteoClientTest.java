package com.nexus.integration.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexus.config.WeatherProperties;
import com.nexus.exception.WeatherUnavailableException;
import com.nexus.model.WeatherCondition;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Pronostico por hora de Open-Meteo y la condicion de una franja. */
class OpenMeteoClientTest {

    /** 2026-10-24 18:00 UTC = 12:00 en la CDMX. */
    private static final long H18 = Instant.parse("2026-10-24T18:00:00Z").getEpochSecond();

    private static final String HOURS =
            "{\"hourly\":{"
                    + "\"time\":[" + H18 + "," + (H18 + 3600) + "," + (H18 + 7200) + "," + (H18 + 10800) + "],"
                    + "\"weather_code\":[0,2,61,0],"
                    + "\"precipitation_probability\":[0,10,80,0]}}";

    private MockRestServiceServer server;
    private OpenMeteoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenMeteoClient(
                builder, new WeatherProperties(URI.create("http://meteo.test"), Duration.ofSeconds(3), true));
    }

    @Test
    @DisplayName("should ask the hourly code and rain probability for the rounded point")
    void shouldAskTheHourlyCodeAndRainProbabilityForTheRoundedPoint() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://meteo.test/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "19.43"))
                .andExpect(queryParam("longitude", "-99.13"))
                .andExpect(queryParam("hourly", "weather_code,precipitation_probability"))
                .andExpect(queryParam("timeformat", "unixtime"))
                .andRespond(withSuccess(HOURS, MediaType.APPLICATION_JSON));

        WeatherCondition condition = client.forecast(
                19.4326, -99.1332, Instant.ofEpochSecond(H18), Instant.ofEpochSecond(H18 + 7200));

        assertThat(condition).isEqualTo(WeatherCondition.CLOUDY);
        server.verify();
    }

    @Test
    @DisplayName("should take the worst hour that overlaps the slot")
    void shouldTakeTheWorstHourThatOverlapsTheSlot() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://meteo.test/v1/forecast")))
                .andRespond(withSuccess(HOURS, MediaType.APPLICATION_JSON));

        // 19:30-20:30 UTC toca las horas de las 19 (nublado) y de las 20 (lluvia).
        WeatherCondition condition = client.forecast(
                19.4, -99.1, Instant.ofEpochSecond(H18 + 5400), Instant.ofEpochSecond(H18 + 9000));

        assertThat(condition).isEqualTo(WeatherCondition.RAINY);
    }

    @Test
    @DisplayName("should be unknown when the slot is beyond the forecast")
    void shouldBeUnknownWhenTheSlotIsBeyondTheForecast() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://meteo.test/v1/forecast")))
                .andRespond(withSuccess(HOURS, MediaType.APPLICATION_JSON));

        WeatherCondition condition = client.forecast(
                19.4, -99.1, Instant.ofEpochSecond(H18 + 86_400), Instant.ofEpochSecond(H18 + 90_000));

        assertThat(condition).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    @DisplayName("should report a failure of the service")
    void shouldReportAFailureOfTheService() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://meteo.test/v1/forecast")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.forecast(19.4, -99.1, Instant.ofEpochSecond(H18), Instant.ofEpochSecond(H18 + 3600)))
                .isInstanceOf(WeatherUnavailableException.class);
    }

    @Test
    @DisplayName("should report an answer with another shape")
    void shouldReportAnAnswerWithAnotherShape() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://meteo.test/v1/forecast")))
                .andRespond(withSuccess("{\"error\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.forecast(19.4, -99.1, Instant.ofEpochSecond(H18), Instant.ofEpochSecond(H18 + 3600)))
                .isInstanceOf(WeatherUnavailableException.class);
    }
}
