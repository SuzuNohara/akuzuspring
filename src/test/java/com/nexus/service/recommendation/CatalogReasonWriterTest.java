package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.entity.Activity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Frase de respaldo: la justificacion del catalogo, o la descripcion recortada si no hay. */
class CatalogReasonWriterTest {

    private static Activity activity(String rationale, String description) {
        return Activity.builder().id(1L).title("t").rationale(rationale).description(description).build();
    }

    @Test
    @DisplayName("usa la justificacion del catalogo")
    void usesRationale() {
        assertThat(CatalogReasonWriter.reasonOf(activity(" Uno. ", "d"))).isEqualTo("Uno.");
    }

    @Test
    @DisplayName("sin justificacion usa la descripcion, recortada a 200 caracteres")
    void fallsBackToDescription() {
        String longText = "x".repeat(250);

        String reason = CatalogReasonWriter.reasonOf(activity(" ", longText));

        assertThat(reason).hasSize(CatalogReasonWriter.MAX_LENGTH).endsWith("…");
    }
}
