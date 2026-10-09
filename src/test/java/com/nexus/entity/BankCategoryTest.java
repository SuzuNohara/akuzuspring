package com.nexus.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Etiquetas y orden del enum de categorías del banco (nexus-KB-02, CU34 L10516). */
class BankCategoryTest {

    @Test
    @DisplayName("should expose the CU34 labels in order")
    void shouldExposeCu34LabelsInOrder() {
        assertThat(BankCategory.values())
                .extracting(BankCategory::label)
                .containsExactly(
                        "Comer y beber",
                        "Cultura y espectáculos",
                        "Aire libre",
                        "Deporte y movimiento",
                        "Aprender juntos",
                        "Juegos y relax",
                        "Otras ideas");
    }
}
