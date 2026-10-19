package com.nexus.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherConditionTest {

    @Test
    @DisplayName("should read the wmo code of an hour")
    void shouldReadTheWmoCodeOfAnHour() {
        assertThat(WeatherCondition.ofHour(0, 0)).isEqualTo(WeatherCondition.SUNNY);
        assertThat(WeatherCondition.ofHour(1, 10)).isEqualTo(WeatherCondition.SUNNY);
        assertThat(WeatherCondition.ofHour(3, 10)).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(WeatherCondition.ofHour(45, 0)).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(WeatherCondition.ofHour(61, 30)).isEqualTo(WeatherCondition.RAINY);
        assertThat(WeatherCondition.ofHour(95, 30)).isEqualTo(WeatherCondition.RAINY);
    }

    @Test
    @DisplayName("should count a likely rain as rainy even with a clear sky code")
    void shouldCountALikelyRainAsRainyEvenWithAClearSkyCode() {
        assertThat(WeatherCondition.ofHour(2, 50)).isEqualTo(WeatherCondition.RAINY);
        assertThat(WeatherCondition.ofHour(2, 49)).isEqualTo(WeatherCondition.CLOUDY);
    }

    @Test
    @DisplayName("should keep the worst hour of a slot")
    void shouldKeepTheWorstHourOfASlot() {
        assertThat(WeatherCondition.worst(List.of(WeatherCondition.SUNNY, WeatherCondition.CLOUDY)))
                .isEqualTo(WeatherCondition.CLOUDY);
        assertThat(WeatherCondition.worst(List.of(WeatherCondition.SUNNY, WeatherCondition.RAINY, WeatherCondition.CLOUDY)))
                .isEqualTo(WeatherCondition.RAINY);
        assertThat(WeatherCondition.worst(List.of())).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    @DisplayName("should let an outdoor activity in only with a weather it accepts")
    void shouldLetAnOutdoorActivityInOnlyWithAWeatherItAccepts() {
        assertThat(WeatherCondition.SUNNY.allows(true, "SUNNY")).isTrue();
        assertThat(WeatherCondition.CLOUDY.allows(true, "SUNNY")).isFalse();
        assertThat(WeatherCondition.CLOUDY.allows(true, "CLOUDY|SUNNY")).isTrue();
        assertThat(WeatherCondition.RAINY.allows(true, "CLOUDY|SUNNY")).isFalse();
        assertThat(WeatherCondition.RAINY.allows(true, "ANY")).isTrue();
    }

    @Test
    @DisplayName("should never filter indoor activities nor without a forecast")
    void shouldNeverFilterIndoorActivitiesNorWithoutAForecast() {
        assertThat(WeatherCondition.RAINY.allows(false, "SUNNY")).isTrue();
        assertThat(WeatherCondition.UNKNOWN.allows(true, "SUNNY")).isTrue();
        assertThat(WeatherCondition.RAINY.allows(true, null)).isTrue();
    }
}
