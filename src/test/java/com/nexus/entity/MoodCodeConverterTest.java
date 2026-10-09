package com.nexus.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.security.FieldCipher;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Ida y vuelta y relleno de {@link MoodCodeConverter} (nexus-EMO-01, R5). */
class MoodCodeConverterTest {

    private static String devKey() {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(raw);
    }

    private final MoodCodeConverter converter = new MoodCodeConverter(new FieldCipher(devKey()));

    @ParameterizedTest
    @ValueSource(
            strings = {"EXCITED", "HAPPY", "CALM", "TIRED", "SAD", "DOWN", "STRESSED", "SURPRISED"})
    @DisplayName("should round trip every catalog code")
    void shouldRoundTripEveryCatalogCode(String code) {
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(code)))
                .isEqualTo(code);
    }

    @Test
    @DisplayName("should produce same length for every code")
    void shouldProduceSameLengthForEveryCode() {
        int expected = converter.convertToDatabaseColumn("CALM").length;

        for (String code : new String[] {"EXCITED", "HAPPY", "SURPRISED", "DOWN"}) {
            assertThat(converter.convertToDatabaseColumn(code)).hasSize(expected);
        }
        assertThat(expected).isEqualTo(61);
    }

    @Test
    @DisplayName("should map null to null both ways")
    void shouldMapNullToNullBothWays() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("should reject code longer than 32")
    void shouldRejectCodeLongerThan32() {
        String tooLong = "A".repeat(33);

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
