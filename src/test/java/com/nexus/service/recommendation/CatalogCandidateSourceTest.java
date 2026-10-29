package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nexus.entity.Activity;
import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.model.WeatherCondition;
import com.nexus.repository.ActivityPlaceView;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.PlaceRepository;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** U7 sobre MySQL - Seleccion por catalogo con la escalera de filtros (R16). */
@ExtendWith(MockitoExtension.class)
class CatalogCandidateSourceTest {

    // Sabado 17-oct-2026, 19:00-21:00 en CDMX (UTC-6) => NIGHT, franja de 120 min
    private static final Instant START = Instant.parse("2026-10-18T01:00:00Z");
    private static final Instant END = Instant.parse("2026-10-18T03:00:00Z");
    private static final Coordinates ZOCALO = new Coordinates(19.4326, -99.1332);

    @Mock private ActivityRepository activities;
    @Mock private PlaceRepository places;
    private CatalogCandidateSource source;
    private final List<Activity> catalog = new ArrayList<>();
    private final List<ActivityPlaceView> placeRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        source = new CatalogCandidateSource(activities, places, new Random(42));
        lenient().when(activities.findAll()).thenReturn(catalog);
        lenient().when(places.findByActivityIds(anyCollection())).thenReturn(placeRows);
    }

    private static Activity act(long id, String title, String type, String scope, int cost, int durMin, String dayparts) {
        return Activity.builder()
                .id(id).slug("a" + id).title(title).description("d").activityType(type)
                .locationScope(scope).durationMin(durMin).durationAvg(durMin).costMxnPp(cost)
                .priceBand("FREE").outdoor(false).dayparts(dayparts).rationale("r" + id).build();
    }

    private static ActivityPlaceView place(long activityId, long placeId, double lat, double lon) {
        return place(activityId, placeId, lat, lon, null);
    }

    static ActivityPlaceView place(long activityId, long placeId, double lat, double lon, Double score) {
        return new ActivityPlaceView() {
            public Long getActivityId() { return activityId; }
            public Long getPlaceId() { return placeId; }
            public String getName() { return "Lugar " + placeId; }
            public String getBorough() { return "Cuauhtemoc"; }
            public Double getLat() { return lat; }
            public Double getLon() { return lon; }
            public Double getScore() { return score; }
        };
    }

    private static RecommendationQuery query(Scope scope, int maxCost, IdeaCategory category, List<Coordinates> refs) {
        return new RecommendationQuery(scope, maxCost, category, START, END, refs);
    }

    private static List<Long> ids(Selection s) {
        return s.candidates().stream().map(c -> c.activity().getId()).toList();
    }

    @Test
    @DisplayName("S1: si alcanzan, no amplia nada; ordena por afinidad y titulo")
    void strictWhenEnough() {
        catalog.add(act(1, "B juego", "BOARD_GAME", "HOME", 0, 60, "ANY"));
        catalog.add(act(2, "A juego", "BOARD_GAME", "HOME", 0, 60, "NIGHT"));
        catalog.add(act(3, "Yoga", "SPORT_SESSION", "HOME", 0, 60, "ANY"));
        CoupleContext ctx = new CoupleContext(Map.of(IdeaCategory.FISICO, 3, IdeaCategory.SOCIAL, 1));

        Selection s = source.select(query(Scope.HOME, 0, null, List.of()), ctx, Set.of(), 3);

        assertThat(ids(s)).containsExactly(3L, 2L, 1L);
        assertThat(s.relaxed()).isEmpty();
        assertThat(s.source()).isEqualTo(SelectionSource.CATALOG);
    }

    private static Activity outdoor(long id, String title, String weatherOk) {
        return Activity.builder()
                .id(id).slug("a" + id).title(title).description("d").activityType("PICNIC")
                .locationScope("HOME").durationMin(60).durationAvg(60).costMxnPp(0)
                .priceBand("FREE").outdoor(true).weatherOk(weatherOk).dayparts("ANY").rationale("r" + id).build();
    }

    @Test
    @DisplayName("clima: con lluvia prevista no salen las de aire libre que piden buen clima, ni ampliando todo")
    void rainKeepsOutdoorPlansOut() {
        catalog.add(outdoor(1, "Picnic", "CLOUDY|SUNNY"));
        catalog.add(outdoor(2, "Bajo la lluvia", "ANY"));
        catalog.add(act(3, "Juego de mesa", "BOARD_GAME", "HOME", 0, 60, "ANY"));
        RecommendationQuery rainy = query(Scope.ANY, 15_000, null, List.of()).withWeather(WeatherCondition.RAINY);

        Selection s = source.select(rainy, CoupleContext.empty(), Set.of(), 5);

        assertThat(ids(s)).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("clima: nublado deja pasar CLOUDY|SUNNY pero no SUNNY")
    void cloudyKeepsSunnyOnlyPlansOut() {
        catalog.add(outdoor(1, "Picnic", "CLOUDY|SUNNY"));
        catalog.add(outdoor(2, "Ver el atardecer", "SUNNY"));
        RecommendationQuery cloudy = query(Scope.ANY, 15_000, null, List.of()).withWeather(WeatherCondition.CLOUDY);

        Selection s = source.select(cloudy, CoupleContext.empty(), Set.of(), 5);

        assertThat(ids(s)).containsExactly(1L);
    }

    @Test
    @DisplayName("clima: sin pronostico no se descarta nada")
    void unknownWeatherFiltersNothing() {
        catalog.add(outdoor(1, "Ver el atardecer", "SUNNY"));

        Selection s = source.select(query(Scope.ANY, 15_000, null, List.of()), CoupleContext.empty(), Set.of(), 5);

        assertThat(ids(s)).containsExactly(1L);
    }

    @Test
    @DisplayName("S2: las excluidas nunca salen")
    void excludedNeverShown() {
        catalog.add(act(1, "Uno", "BOARD_GAME", "HOME", 0, 60, "ANY"));
        catalog.add(act(2, "Dos", "BOARD_GAME", "HOME", 0, 60, "ANY"));

        Selection s = source.select(query(Scope.ANY, 15_000, null, List.of()), CoupleContext.empty(), Set.of(1L), 5);

        assertThat(ids(s)).containsExactly(2L);
    }

    @Test
    @DisplayName("S3: una actividad que no cabe en la franja no sale ni ampliando todo")
    void slotIsNeverRelaxed() {
        catalog.add(act(1, "Largo", "HIKE", "HOME", 0, 180, "ANY"));

        Selection s = source.select(query(Scope.HOME, 0, null, List.of()), CoupleContext.empty(), Set.of(), 5);

        assertThat(s.candidates()).isEmpty();
    }

    @Test
    @DisplayName("S4/S11: en la ciudad usa el lugar mas cercano dentro de 15 km; si ninguno, no sale")
    void cityNeedsPlaceWithinRadius() {
        catalog.add(act(1, "Cine", "FILM", "CITY", 100, 60, "ANY"));
        catalog.add(act(2, "Lejos", "FILM", "CITY", 100, 60, "ANY"));
        placeRows.add(place(1, 10, 19.3500, -99.1620)); // Coyoacan, ~9.6 km
        placeRows.add(place(1, 11, 19.4330, -99.1340)); // junto al Zocalo
        placeRows.add(place(2, 20, 19.2571, -99.1036)); // Xochimilco, ~19.7 km

        Selection s = source.select(query(Scope.CITY, 500, null, List.of(ZOCALO)), CoupleContext.empty(), Set.of(), 5);

        assertThat(ids(s)).containsExactly(1L);
        assertThat(s.candidates().get(0).place().placeId()).isEqualTo(11L);
        assertThat(s.candidates().get(0).place().distanceKm()).isLessThan(1.0);
    }

    @Test
    @DisplayName("sin referencias, una actividad de ciudad sale con su primer lugar y sin distancia")
    void cityWithoutReferences() {
        catalog.add(act(1, "Cine", "FILM", "CITY", 100, 60, "ANY"));
        placeRows.add(place(1, 10, 19.35, -99.16));

        Selection s = source.select(query(Scope.ANY, 500, null, List.of()), CoupleContext.empty(), Set.of(), 5);

        assertThat(s.candidates().get(0).place().placeId()).isEqualTo(10L);
        assertThat(s.candidates().get(0).place().distanceKm()).isNull();
    }

    @Test
    @DisplayName("S5: si la categoria no alcanza, se completa con otras y se avisa")
    void relaxesCategory() {
        catalog.add(act(1, "Yoga", "SPORT_SESSION", "HOME", 0, 60, "ANY"));
        catalog.add(act(2, "Juego", "BOARD_GAME", "HOME", 0, 60, "ANY"));

        Selection s = source.select(query(Scope.HOME, 0, IdeaCategory.FISICO, List.of()), CoupleContext.empty(), Set.of(), 2);

        assertThat(ids(s)).containsExactly(1L, 2L);
        assertThat(s.relaxed()).containsExactly(RelaxedFilter.CATEGORY);
    }

    @Test
    @DisplayName("S7: con el caso mas estricto igual junta opciones subiendo presupuesto y alcance")
    void strictestStillFills() {
        catalog.add(act(1, "Cena", "DINNER", "CITY", 800, 90, "MORNING"));
        catalog.add(act(2, "Juego", "BOARD_GAME", "HOME", 0, 60, "ANY"));
        placeRows.add(place(1, 10, 19.4330, -99.1340));

        Selection s = source.select(query(Scope.HOME, 0, IdeaCategory.ESPIRITUAL, List.of(ZOCALO)), CoupleContext.empty(), Set.of(), 2);

        assertThat(ids(s)).containsExactly(2L, 1L);
        assertThat(s.relaxed()).contains(RelaxedFilter.CATEGORY, RelaxedFilter.DAYPART, RelaxedFilter.BUDGET, RelaxedFilter.SCOPE);
    }

    @Test
    @DisplayName("el momento del dia se respeta mientras haya opciones")
    void daypartRespected() {
        catalog.add(act(1, "Desayuno", "COFFEE", "HOME", 0, 60, "MORNING"));
        catalog.add(act(2, "Pelicula", "FILM", "HOME", 0, 60, "NIGHT"));

        Selection s = source.select(query(Scope.HOME, 0, null, List.of()), CoupleContext.empty(), Set.of(), 1);

        assertThat(ids(s)).containsExactly(2L);
    }

    // [kb-lugares] U7 - Eleccion del lugar con la popularidad (places.score).

    @Test
    @DisplayName("[kb-lugares] P7.1: con referencias elige el lugar mas cercano a 15 km o menos")
    void nearestWithinRadius() {
        List<ActivityPlaceView> options = List.of(place(1, 10, 19.3500, -99.1620, 99.0), place(1, 11, 19.4330, -99.1340, 1.0));

        Optional<PlaceChoice> chosen = CatalogCandidateSource.nearestValidPlace(options, List.of(ZOCALO), 15, new Random(1));

        assertThat(chosen).map(PlaceChoice::placeId).contains(11L);
    }

    @Test
    @DisplayName("[kb-lugares] P7.2: a la misma distancia gana el mas popular")
    void sameDistanceMostPopularWins() {
        List<ActivityPlaceView> options = List.of(place(1, 10, 19.4330, -99.1340, 2.0), place(1, 11, 19.4330, -99.1340, 8.0));

        Optional<PlaceChoice> chosen = CatalogCandidateSource.nearestValidPlace(options, List.of(ZOCALO), 15, new Random(1));

        assertThat(chosen).map(PlaceChoice::placeId).contains(11L);
    }

    @Test
    @DisplayName("[kb-lugares] P7.3: si ningun lugar esta a 15 km o menos, no hay lugar")
    void noPlaceWithinRadius() {
        List<ActivityPlaceView> options = List.of(place(1, 20, 19.2571, -99.1036, 50.0));

        assertThat(CatalogCandidateSource.nearestValidPlace(options, List.of(ZOCALO), 15, new Random(1))).isEmpty();
    }

    @Test
    @DisplayName("[kb-lugares] P7.4: Sin zona elige al azar entre los 5 mas populares")
    void noZonePicksAmongTheFiveMostPopular() {
        List<ActivityPlaceView> options = LongStream.rangeClosed(1, 8)
                .mapToObj(i -> place(1, i, 19.35, -99.16, (double) i))
                .toList();

        Set<Long> picked = LongStream.range(0, 50)
                .mapToObj(seed -> CatalogCandidateSource.nearestValidPlace(options, List.of(), 15, new Random(seed)))
                .map(choice -> choice.orElseThrow().placeId())
                .collect(Collectors.toSet());

        assertThat(picked).isSubsetOf(4L, 5L, 6L, 7L, 8L).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("[kb-lugares] P7.5: Sin zona con menos de 5 lugares elige entre esos y sin distancia")
    void noZoneWithFewPlaces() {
        List<ActivityPlaceView> options = List.of(place(1, 1, 19.35, -99.16, null), place(1, 2, 19.35, -99.16, 3.0), place(1, 3, 19.35, -99.16, 1.0));

        Optional<PlaceChoice> chosen = CatalogCandidateSource.nearestValidPlace(options, List.of(), 15, new Random(7));

        assertThat(chosen).map(PlaceChoice::placeId).get().isIn(1L, 2L, 3L);
        assertThat(chosen).map(PlaceChoice::distanceKm).isEmpty();
        assertThat(CatalogCandidateSource.nearestValidPlace(List.of(), List.of(), 15, new Random(7))).isEmpty();
    }

    @Test
    @DisplayName("[rango-distancia] elige el lugar dentro de la distancia elegida, o ninguno")
    void nearestWithinChosenRadius() {
        double kmPerDegree = com.nexus.util.GeoDistance.EARTH_RADIUS_KM * Math.PI / 180;
        List<ActivityPlaceView> options = List.of(
                place(1, 2, ZOCALO.lat() + 2 / kmPerDegree, ZOCALO.lon(), 1.0),
                place(1, 4, ZOCALO.lat() + 4 / kmPerDegree, ZOCALO.lon(), 9.0),
                place(1, 12, ZOCALO.lat() + 12 / kmPerDegree, ZOCALO.lon(), 9.0));

        assertThat(CatalogCandidateSource.nearestValidPlace(options, List.of(ZOCALO), 3, new Random(1)))
                .map(PlaceChoice::placeId).contains(2L);
        assertThat(CatalogCandidateSource.nearestValidPlace(options, List.of(ZOCALO), 1, new Random(1))).isEmpty();
        assertThat(CatalogCandidateSource.nearestValidPlace(options, List.of(ZOCALO), 15, new Random(1)))
                .map(PlaceChoice::placeId).contains(2L);
        assertThat(CatalogCandidateSource.nearestValidPlace(options, List.of(), 1, new Random(1))).isPresent();
    }
}
