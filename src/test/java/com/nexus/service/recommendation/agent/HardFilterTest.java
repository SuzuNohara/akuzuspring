package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexus.entity.Activity;
import com.nexus.integration.qdrant.QdrantFilter;
import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.model.WeatherCondition;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.PlaceRepository;
import com.nexus.service.ProximityFilter;
import com.nexus.service.ProximityFilter.Coordinates;
import com.nexus.service.recommendation.CatalogCandidateSource;
import com.nexus.service.recommendation.Daypart;
import com.nexus.service.recommendation.LadderStep;
import com.nexus.service.recommendation.RecommendationQuery;
import com.nexus.service.recommendation.RelaxationLadder;
import com.nexus.service.recommendation.RelaxedFilter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [t06b-agente-java] U6 - Filtro duro de Qdrant por escalon de la escalera (R12). */
class HardFilterTest {

    /** 20:00 en la CDMX: franja de noche. */
    private static final Instant NIGHT_START = Instant.parse("2026-10-24T02:00:00Z");
    private static final Coordinates HOME_OF_A = new Coordinates(19.43, -99.13);

    private static RecommendationQuery query(Scope scope, int maxCost, Set<IdeaCategory> categories, long minutes) {
        return new RecommendationQuery(
                scope, maxCost, categories, NIGHT_START, NIGHT_START.plusSeconds(minutes * 60), List.of(HOME_OF_A),
                WeatherCondition.RAINY);
    }

    private static LadderStep first(RecommendationQuery q) {
        return RelaxationLadder.steps(q).get(0);
    }

    private static LadderStep stepWith(RecommendationQuery q, RelaxedFilter last) {
        return RelaxationLadder.steps(q).stream()
                .filter(s -> s.relaxed().contains(last))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("U6.1: should require the daypart on the first step")
    void shouldRequireTheDaypartOnTheFirstStep() {
        QdrantFilter filter = HardFilter.of(first(query(Scope.CITY, 300, Set.of(IdeaCategory.SOCIAL), 90)),
                Daypart.NIGHT, Set.of());

        assertThat(filter.daypart()).isEqualTo("NIGHT");
        assertThat(filter.scopes()).containsExactly("CITY");
        assertThat(filter.dimensions()).containsExactly("SOCIAL");
        assertThat(filter.maxCostMxn()).isEqualTo(300);
    }

    @Test
    @DisplayName("U6.2: should stop requiring the daypart from the DAYPART step on")
    void shouldStopRequiringTheDaypartFromTheDaypartStepOn() {
        RecommendationQuery q = query(Scope.CITY, 300, Set.of(IdeaCategory.SOCIAL), 90);

        QdrantFilter filter = HardFilter.of(stepWith(q, RelaxedFilter.DAYPART), Daypart.NIGHT, Set.of());

        assertThat(filter.daypart()).isNull();
        assertThat(filter.dimensions()).isEmpty();
    }

    @Test
    @DisplayName("U6.3: should use the widened budget on the BUDGET step")
    void shouldUseTheWidenedBudgetOnTheBudgetStep() {
        RecommendationQuery q = query(Scope.CITY, 300, Set.of(), 90);

        QdrantFilter filter = HardFilter.of(stepWith(q, RelaxedFilter.BUDGET), Daypart.NIGHT, Set.of());

        assertThat(filter.maxCostMxn()).isGreaterThan(300);
    }

    @Test
    @DisplayName("U6.4: should drop the scope restriction on the SCOPE step")
    void shouldDropTheScopeRestrictionOnTheScopeStep() {
        RecommendationQuery q = query(Scope.CITY, 300, Set.of(), 90);

        QdrantFilter filter = HardFilter.of(stepWith(q, RelaxedFilter.SCOPE), Daypart.NIGHT, Set.of());

        assertThat(filter.scopes()).containsExactlyInAnyOrder("HOME", "CITY");
    }

    @Test
    @DisplayName("U6.5: should always carry minimum duration, distance, weather and exclusions")
    void shouldAlwaysCarryDurationDistanceWeatherAndExclusions() {
        RecommendationQuery q = query(Scope.CITY, 300, Set.of(IdeaCategory.SOCIAL), 90);

        for (LadderStep step : RelaxationLadder.steps(q)) {
            QdrantFilter filter = HardFilter.of(step, Daypart.NIGHT, Set.of(7L));

            assertThat(filter.maxDurationMin()).isEqualTo(90);
            assertThat(filter.within()).containsExactly(HOME_OF_A);
            assertThat(filter.radiusM()).isEqualTo(15_000);
            assertThat(filter.weather()).isEqualTo(WeatherCondition.RAINY);
            assertThat(filter.excludedActivityIds()).containsExactly(7L);
        }
    }

    @Test
    @DisplayName("U6.6: should accept, step by step, the same activities as the MySQL catalog")
    void shouldAcceptStepByStepTheSameActivitiesAsTheMysqlCatalog() {
        List<Activity> catalog = List.of(
                OfferCatalogTest.activity(1, "CITY").activityType("FILM").dayparts("NIGHT").costMxnPp(200).build(),
                OfferCatalogTest.activity(2, "CITY").activityType("FILM").dayparts("AFTERNOON").costMxnPp(200).build(),
                OfferCatalogTest.activity(3, "CITY").activityType("DINNER").dayparts("ANY").costMxnPp(250).build(),
                OfferCatalogTest.activity(4, "CITY").activityType("CONCERT").dayparts("NIGHT").costMxnPp(900).build(),
                OfferCatalogTest.activity(5, "HOME").activityType("BOARD_GAME").dayparts("NIGHT").costMxnPp(0).build(),
                OfferCatalogTest.activity(6, "CITY").activityType("HIKE").dayparts("NIGHT").costMxnPp(0)
                        .outdoor(true).weatherOk("SUNNY|CLOUDY").build(),
                OfferCatalogTest.activity(7, "CITY").activityType("FILM").dayparts("NIGHT").costMxnPp(100).build(),
                OfferCatalogTest.activity(8, "CITY").activityType("WALK").dayparts("ANY").costMxnPp(0)
                        .outdoor(true).weatherOk("ANY").build(),
                OfferCatalogTest.activity(9, "CITY").activityType("COFFEE").dayparts("NIGHT").costMxnPp(80)
                        .durationMin(120).durationAvg(150).durationMax(180).build(),
                OfferCatalogTest.activity(10, "HOME").activityType("COOKING").dayparts("AFTERNOON").costMxnPp(300)
                        .build());
        List<Offer> offers = offersOf(catalog);
        Set<Long> excluded = Set.of(7L);
        RecommendationQuery q = new RecommendationQuery(
                Scope.CITY, 300, Set.of(IdeaCategory.SOCIAL), NIGHT_START, NIGHT_START.plusSeconds(90 * 60), List.of(),
                WeatherCondition.RAINY);

        for (LadderStep step : RelaxationLadder.steps(q)) {
            Map<String, Object> filter = HardFilter.of(step, Daypart.NIGHT, excluded).toJson();
            Set<Long> byQdrant = offers.stream()
                    .filter(o -> passes(filter, OfferIndexer.payload(o, "h")))
                    .map(Offer::activityId)
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<Long> byMysql = catalog.stream()
                    .filter(a -> !excluded.contains(a.getId()))
                    .filter(a -> CatalogCandidateSource.matches(a, step, Daypart.NIGHT))
                    .map(Activity::getId)
                    .collect(Collectors.toCollection(TreeSet::new));

            assertThat(byQdrant).as("escalon %s", step.relaxed()).isEqualTo(byMysql);
        }
    }

    /** Ofertas del catalogo: un lugar por actividad en la ciudad. */
    private static List<Offer> offersOf(List<Activity> catalog) {
        ActivityRepository activities = mock(ActivityRepository.class);
        PlaceRepository places = mock(PlaceRepository.class);
        when(activities.findAllWithInterests()).thenReturn(catalog);
        when(places.findAllActivityPlaces()).thenReturn(catalog.stream()
                .filter(a -> "CITY".equals(a.getLocationScope()))
                .map(a -> OfferCatalogTest.link(a.getId(), 100 + a.getId(), 19.40, -99.15))
                .toList());
        return new OfferCatalog(activities, places).load().offers();
    }

    // Evaluador en memoria del subconjunto de filtros de Qdrant que produce QdrantFilter.

    @SuppressWarnings("unchecked")
    private static boolean passes(Map<String, Object> filter, Map<String, Object> payload) {
        List<Map<String, Object>> must = (List<Map<String, Object>>) filter.getOrDefault("must", List.of());
        List<Map<String, Object>> mustNot = (List<Map<String, Object>>) filter.getOrDefault("must_not", List.of());
        return must.stream().allMatch(c -> holds(c, payload)) && mustNot.stream().noneMatch(c -> holds(c, payload));
    }

    @SuppressWarnings("unchecked")
    private static boolean holds(Map<String, Object> condition, Map<String, Object> payload) {
        if (condition.containsKey("should")) {
            return ((List<Map<String, Object>>) condition.get("should")).stream().anyMatch(c -> holds(c, payload));
        }
        Object value = payload.get((String) condition.get("key"));
        if (condition.containsKey("match")) {
            Map<String, Object> match = (Map<String, Object>) condition.get("match");
            Collection<?> wanted = match.containsKey("any") ? (Collection<?>) match.get("any") : List.of(match.get("value"));
            Set<String> wantedText = wanted.stream().map(String::valueOf).collect(Collectors.toSet());
            Collection<?> actual = value instanceof Collection<?> list ? list : List.of(String.valueOf(value));
            return actual.stream().map(String::valueOf).anyMatch(wantedText::contains);
        }
        if (condition.containsKey("range")) {
            Number lte = (Number) ((Map<String, Object>) condition.get("range")).get("lte");
            return value instanceof Number n && n.doubleValue() <= lte.doubleValue();
        }
        Map<String, Object> geo = (Map<String, Object>) condition.get("geo_radius");
        Map<String, Object> center = (Map<String, Object>) geo.get("center");
        if (!(value instanceof Map<?, ?> location)) {
            return false;
        }
        double km = ProximityFilter.distanceKm(
                new Coordinates((Double) location.get("lat"), (Double) location.get("lon")),
                new Coordinates((Double) center.get("lat"), (Double) center.get("lon")));
        return km * 1000 <= ((Number) geo.get("radius")).doubleValue();
    }
}
