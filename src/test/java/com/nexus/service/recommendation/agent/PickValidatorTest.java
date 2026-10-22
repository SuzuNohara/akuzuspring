package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.entity.Activity;
import com.nexus.model.ReasonSource;
import com.nexus.service.recommendation.Candidate;
import com.nexus.service.recommendation.agent.PickValidator.ValidationResult;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** [t06b-agente-java] U11 - Validador de las elecciones de Mistral. */
class PickValidatorTest {

    private static final String GOOD = "Una tarde tranquila para platicar sin prisa.";

    private static final List<Candidate> SHOWN = LongStream.rangeClosed(1, 6)
            .mapToObj(id -> new Candidate(
                    Activity.builder().id(id).title("t" + id).rationale("Frase del catalogo " + id + ".").build(),
                    null,
                    1.0 - id * 0.1))
            .toList();

    private static String picks(String... items) {
        return "{\"picks\":[" + String.join(",", items) + "]}";
    }

    private static String pick(Object ref, String why) {
        return "{\"ref\":" + ref + ",\"why\":\"" + why + "\"}";
    }

    @Test
    @DisplayName("U11.1: should keep five valid picks with the agent as source")
    void shouldKeepFiveValidPicksWithTheAgentAsSource() {
        ValidationResult result = PickValidator.validate(
                picks(pick(3, GOOD), pick(1, GOOD), pick(5, GOOD), pick(2, GOOD), pick(6, GOOD)), SHOWN, 5);

        assertThat(result.picks()).extracting(WrittenPick::ref).containsExactly(3, 1, 5, 2, 6);
        assertThat(result.picks()).extracting(p -> p.candidate().activity().getId())
                .containsExactly(3L, 1L, 5L, 2L, 6L);
        assertThat(result.picks()).allMatch(p -> p.source() == ReasonSource.AGENT && p.reason().equals(GOOD));
        assertThat(result.dropped()).isZero();
        assertThat(result.replaced()).isZero();
    }

    @Test
    @DisplayName("U11.2: should drop refs out of range or repeated")
    void shouldDropRefsOutOfRangeOrRepeated() {
        ValidationResult result = PickValidator.validate(
                picks(pick(0, GOOD), pick(7, GOOD), pick(2, GOOD), pick(2, GOOD)), SHOWN, 5);

        assertThat(result.picks()).extracting(WrittenPick::ref).containsExactly(2);
        assertThat(result.dropped()).isEqualTo(3);
    }

    @Test
    @DisplayName("U11.3: should ignore refs written as text or as a boolean")
    void shouldIgnoreRefsWrittenAsTextOrAsABoolean() {
        ValidationResult result =
                PickValidator.validate(picks(pick("\"3\"", GOOD), pick(true, GOOD), pick(4, GOOD)), SHOWN, 5);

        assertThat(result.picks()).extracting(WrittenPick::ref).containsExactly(4);
        assertThat(result.dropped()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Muy corta de 19 car",
        "Mira el sitio https://nexus.mx para ver mas detalles de la actividad",
        "Escribe a hola@nexus.mx para reservar su lugar",
        "Ideal porque hoy los dos están cansados.",
        "Perfecta porque tu pareja está triste esta semana."
    })
    @DisplayName("U11.4: should replace an invalid reason with the catalog one")
    void shouldReplaceAnInvalidReasonWithTheCatalogOne(String why) {
        ValidationResult result = PickValidator.validate(picks(pick(2, why)), SHOWN, 5);

        WrittenPick written = result.picks().get(0);
        assertThat(written.reason()).isEqualTo("Frase del catalogo 2.");
        assertThat(written.source()).isEqualTo(ReasonSource.CATALOG);
        assertThat(result.replaced()).isEqualTo(1);
    }

    @Test
    @DisplayName("U11.4: should accept exactly 20 and 140 characters and replace 19 and 141")
    void shouldAcceptTheLengthLimitsAndReplaceJustOutside() {
        String at20 = "a".repeat(20);
        String at140 = "b".repeat(140);
        String at19 = "c".repeat(19);
        String at141 = "d".repeat(141);

        ValidationResult result = PickValidator.validate(
                picks(pick(1, at20), pick(2, at140), pick(3, at19), pick(4, at141)), SHOWN, 5);

        assertThat(result.picks()).extracting(WrittenPick::source).containsExactly(
                ReasonSource.AGENT, ReasonSource.AGENT, ReasonSource.CATALOG, ReasonSource.CATALOG);
        assertThat(result.replaced()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"picks\":", "{\"items\":[]}", "[]", "", "null"})
    @DisplayName("U11.5: should treat broken JSON or a missing picks list as an empty answer")
    void shouldTreatBrokenJsonOrAMissingPicksListAsAnEmptyAnswer(String json) {
        ValidationResult result = PickValidator.validate(json, SHOWN, 5);

        assertThat(result.picks()).isEmpty();
        assertThat(result.dropped()).isZero();
        assertThat(result.replaced()).isZero();
    }

    @Test
    @DisplayName("U11.6: should keep only the first k picks")
    void shouldKeepOnlyTheFirstKPicks() {
        ValidationResult result = PickValidator.validate(
                picks(pick(1, GOOD), pick(2, GOOD), pick(3, GOOD), pick(4, GOOD)), SHOWN, 2);

        assertThat(result.picks()).extracting(WrittenPick::ref).containsExactly(1, 2);
        assertThat(result.dropped()).isEqualTo(2);
    }
}
