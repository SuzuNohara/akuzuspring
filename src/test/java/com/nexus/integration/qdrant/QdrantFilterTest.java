package com.nexus.integration.qdrant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/** [T-06] Q3 - Traduccion del filtro de candidatas al JSON de Qdrant. */
class QdrantFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Q3: should translate every condition to the exact Qdrant JSON")
    void shouldTranslateEveryConditionToTheExactQdrantJson() throws Exception {
        QdrantFilter filter =
                new QdrantFilter(
                        Set.of("CITY"),
                        800,
                        Set.of("EMOCIONAL"),
                        "NIGHT",
                        120,
                        new LinkedHashSet<>(List.of(7L, 9L)));

        String json = mapper.writeValueAsString(filter.toJson());

        JSONAssert.assertEquals(
                "{\"must\":["
                        + "{\"key\":\"location_scope\",\"match\":{\"any\":[\"CITY\"]}},"
                        + "{\"key\":\"cost_mxn_pp\",\"range\":{\"lte\":800}},"
                        + "{\"key\":\"dimension\",\"match\":{\"any\":[\"EMOCIONAL\"]}},"
                        + "{\"key\":\"dayparts\",\"match\":{\"any\":[\"NIGHT\",\"ANY\"]}},"
                        + "{\"key\":\"duration_min\",\"range\":{\"lte\":120}}],"
                        + "\"must_not\":[{\"has_id\":[7,9]}]}",
                json,
                JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("Q3: should omit the conditions that are null or empty")
    void shouldOmitTheConditionsThatAreNullOrEmpty() throws Exception {
        QdrantFilter filter = new QdrantFilter(Set.of("HOME"), null, Set.of(), null, null, Set.of());

        String json = mapper.writeValueAsString(filter.toJson());

        JSONAssert.assertEquals(
                "{\"must\":[{\"key\":\"location_scope\",\"match\":{\"any\":[\"HOME\"]}}]}",
                json,
                JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("Q3: should produce an empty filter when nothing is restricted")
    void shouldProduceAnEmptyFilterWhenNothingIsRestricted() {
        QdrantFilter filter = new QdrantFilter(null, null, null, null, null, null);

        assertThat(filter.toJson()).isEmpty();
    }
}
