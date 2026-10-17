package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.entity.Activity;
import com.nexus.model.ReasonSource;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Frase de respaldo: la justificacion del catalogo, o la descripcion recortada si no hay. */
class CatalogReasonWriterTest {

    private final CatalogReasonWriter writer = new CatalogReasonWriter();

    private static Candidate candidate(long id, String rationale, String description) {
        Activity a = Activity.builder().id(id).title("t").rationale(rationale).description(description).build();
        return new Candidate(a, null, 0);
    }

    @Test
    @DisplayName("usa la justificacion del catalogo, en el mismo orden, marcada como CATALOG")
    void usesRationale() {
        List<WrittenReason> out =
                writer.write(List.of(candidate(1, "Uno.", "d"), candidate(2, "Dos.", "d")), CoupleContext.empty());

        assertThat(out).extracting(WrittenReason::activityId).containsExactly(1L, 2L);
        assertThat(out).extracting(WrittenReason::reason).containsExactly("Uno.", "Dos.");
        assertThat(out).allMatch(r -> r.source() == ReasonSource.CATALOG);
    }

    @Test
    @DisplayName("sin justificacion usa la descripcion, recortada a 200 caracteres")
    void fallsBackToDescription() {
        String longText = "x".repeat(250);

        WrittenReason r = writer.write(List.of(candidate(1, " ", longText)), CoupleContext.empty()).get(0);

        assertThat(r.reason()).hasSize(CatalogReasonWriter.MAX_LENGTH).endsWith("…");
    }
}
