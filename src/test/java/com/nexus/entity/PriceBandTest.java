package com.nexus.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Bandas de precio por pareja (nexus-KB-02, K2, R10). */
class PriceBandTest {

    @ParameterizedTest
    @CsvSource({
        "0, FREE",
        "1, LOW",
        "200, LOW",
        "201, MID",
        "500, MID",
        "501, HIGH",
        "1000, HIGH",
        "1001, PREMIUM"
    })
    @DisplayName("should map the couple cost to the right band at the boundaries")
    void shouldMapCoupleCostToBand(int mxn, PriceBand expected) {
        assertThat(PriceBand.ofCoupleCost(mxn)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should reject a negative cost")
    void shouldRejectNegativeCost() {
        assertThatThrownBy(() -> PriceBand.ofCoupleCost(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
