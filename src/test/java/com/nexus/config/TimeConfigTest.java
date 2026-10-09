package com.nexus.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El reloj de la app usa la zona de Ciudad de México (nexus-EMO-01, RN-38). */
class TimeConfigTest {

    @Test
    @DisplayName("should use America/Mexico_City zone for the application clock")
    void shouldUseMexicoCityZone() {
        Clock clock = new TimeConfig().appClock();

        assertThat(clock.getZone().getId()).isEqualTo("America/Mexico_City");
    }
}
