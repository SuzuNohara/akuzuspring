package com.nexus.integration.qdrant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.model.WeatherCondition;
import com.nexus.service.ProximityFilter.Coordinates;
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

    private static final Coordinates MINE = new Coordinates(19.43, -99.13);
    private static final Coordinates PARTNER = new Coordinates(19.36, -99.18);

    private static String geo(Coordinates c) {
        return "{\"key\":\"location\",\"geo_radius\":{\"center\":{\"lat\":" + c.lat() + ",\"lon\":" + c.lon()
                + "},\"radius\":15000}}";
    }

    private String json(QdrantFilter filter) throws Exception {
        return mapper.writeValueAsString(filter.toJson());
    }

    @Test
    @DisplayName("U3.1: should require a 15 km geo radius around each reference in the city")
    void shouldRequireAGeoRadiusAroundEachReferenceInTheCity() throws Exception {
        QdrantFilter filter = new QdrantFilter(
                Set.of("CITY"), null, null, null, null, null, List.of(MINE, PARTNER), 15_000, null, null);

        JSONAssert.assertEquals(
                "{\"must\":[{\"key\":\"location_scope\",\"match\":{\"any\":[\"CITY\"]}},"
                        + geo(MINE) + "," + geo(PARTNER) + "]}",
                json(filter),
                JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("U3.1: should also admit offers at home when the scope allows HOME")
    void shouldAlsoAdmitOffersAtHomeWhenTheScopeAllowsHome() throws Exception {
        QdrantFilter filter = new QdrantFilter(
                new LinkedHashSet<>(List.of("HOME", "CITY")), null, null, null, null, null,
                List.of(MINE), 15_000, null, null);

        JSONAssert.assertEquals(
                "{\"must\":[{\"key\":\"location_scope\",\"match\":{\"any\":[\"HOME\",\"CITY\"]}},"
                        + "{\"should\":[{\"key\":\"location_scope\",\"match\":{\"value\":\"HOME\"}}," + geo(MINE) + "]}]}",
                json(filter),
                JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("U3.2: should let rainy weather pass indoor offers or those that accept rain")
    void shouldLetRainyWeatherPassIndoorOffersOrThoseThatAcceptRain() throws Exception {
        QdrantFilter filter = new QdrantFilter(
                null, null, null, null, null, null, null, 0, WeatherCondition.RAINY, null);

        JSONAssert.assertEquals(
                "{\"must\":[{\"should\":[{\"key\":\"is_outdoor\",\"match\":{\"value\":false}},"
                        + "{\"key\":\"weather_ok\",\"match\":{\"any\":[\"RAINY\",\"ANY\"]}}]}]}",
                json(filter),
                JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("U3.2: should not restrict anything when the weather is unknown")
    void shouldNotRestrictAnythingWhenTheWeatherIsUnknown() {
        QdrantFilter filter = new QdrantFilter(
                null, null, null, null, null, null, null, 0, WeatherCondition.UNKNOWN, null);

        assertThat(filter.toJson()).isEmpty();
    }

    @Test
    @DisplayName("U3.3: should exclude activities by activity_id, not by point id")
    void shouldExcludeActivitiesByActivityIdNotByPointId() throws Exception {
        QdrantFilter filter = new QdrantFilter(
                null, null, null, null, null, null, null, 0, null, new LinkedHashSet<>(List.of(7L, 9L)));

        JSONAssert.assertEquals(
                "{\"must_not\":[{\"key\":\"activity_id\",\"match\":{\"any\":[7,9]}}]}",
                json(filter),
                JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("U3.4: should not restrict anything with the new fields null or empty")
    void shouldNotRestrictAnythingWithTheNewFieldsNullOrEmpty() {
        QdrantFilter filter = new QdrantFilter(null, null, null, null, null, null, List.of(), 15_000, null, Set.of());

        assertThat(filter.toJson()).isEmpty();
    }

    @Test
    @DisplayName("Q3: should produce an empty filter when nothing is restricted")
    void shouldProduceAnEmptyFilterWhenNothingIsRestricted() {
        QdrantFilter filter = new QdrantFilter(null, null, null, null, null, null);

        assertThat(filter.toJson()).isEmpty();
    }
}
