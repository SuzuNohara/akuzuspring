package com.nexus.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [horario-3am] La hora de los horarios para citas es hora de reloj (09:00 en CDMX), no un instante:
 * se guarda y se lee tal cual, sin que la zona de la JVM ni hibernate.jdbc.time_zone la muevan.
 */
class WallClockTimeConverterTest {

    private final WallClockTimeConverter converter = new WallClockTimeConverter();

    @Test
    @DisplayName("writes the wall-clock time as is")
    void writesTheWallClockTimeAsIs() {
        assertThat(converter.convertToDatabaseColumn(LocalTime.of(9, 0))).isEqualTo("09:00:00");
        assertThat(converter.convertToDatabaseColumn(LocalTime.of(22, 30))).isEqualTo("22:30:00");
    }

    @Test
    @DisplayName("reads the factory default 09:00:00 as 9 am, not 3 am")
    void readsTheFactoryDefaultAsNineAm() {
        assertThat(converter.convertToEntityAttribute("09:00:00")).isEqualTo(LocalTime.of(9, 0));
        assertThat(converter.convertToEntityAttribute("22:00")).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("keeps nulls as nulls")
    void keepsNulls() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
