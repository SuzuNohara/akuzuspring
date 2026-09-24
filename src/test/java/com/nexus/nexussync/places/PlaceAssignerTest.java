package com.nexus.nexussync.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.Daypart;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.catalog.Place;
import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Constraints;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Level;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.context.Weather;
import com.nexus.nexussync.context.Window;
import com.nexus.nexussync.params.PlaceParams;
import com.nexus.nexussync.params.PlaceRelax;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlaceAssignerTest {

  private static final double KM_PER_DEGREE_LAT = 111.195;
  private static final Location ZOCALO = new Location(19.432608, -99.133209);
  private static final Location COYOACAN = new Location(19.349914, -99.16217);
  private static final Location MIDPOINT = Geo.midpoint(ZOCALO, COYOACAN);
  private static final List<PlaceRelax> DEFAULT_RELAX =
      List.of(PlaceRelax.RADIUS, PlaceRelax.HOURS_UNKNOWN, PlaceRelax.WEATHER);
  private static final PlaceParams PARAMS = new PlaceParams(15.0, 3, DEFAULT_RELAX, false);
  private static final Window SATURDAY =
      new Window(DayOfWeek.SATURDAY, LocalTime.of(12, 0), LocalTime.of(14, 0), Daypart.AFTERNOON);
  private static final Optional<String> OPEN = Optional.of("24/7");
  private static final String CITY = "cine";
  private static final String HOME = "juegos-mesa";

  private static Place place(
      String id, double kmNorthOfMidpoint, Optional<String> hours, boolean outdoor, boolean ok) {
    double lat = MIDPOINT.lat() + kmNorthOfMidpoint / KM_PER_DEGREE_LAT;
    return new Place(
        id, "name-" + id, "cinema", lat, MIDPOINT.lon(), "Coyoacan", hours, outdoor, ok);
  }

  private static Place indoor(String id, double km, boolean verified) {
    return place(id, km, OPEN, false, verified);
  }

  private static Activity activity(String id, LocationScope scope) {
    return new Activity(
        id,
        id,
        "CULTURE",
        scope,
        Set.of(),
        Set.of(),
        Set.of(Daypart.AFTERNOON),
        Set.of("ANY"),
        60,
        90,
        120,
        1,
        1,
        5,
        1,
        100,
        "LOW",
        false,
        Set.of(),
        Set.of(),
        "");
  }

  private static Catalog catalog(Place... places) {
    Map<String, Place> byId =
        Arrays.stream(places).collect(Collectors.toMap(Place::placeId, Function.identity()));
    Map<String, List<String>> links = new LinkedHashMap<>();
    links.put(CITY, Arrays.stream(places).map(Place::placeId).toList());
    return new Catalog(
        Map.of(CITY, activity(CITY, LocationScope.CITY), HOME, activity(HOME, LocationScope.HOME)),
        byId,
        links);
  }

  private static Profile profile(int id, Optional<Location> location) {
    Constraints constraints =
        new Constraints("MID", "LOCAL", DayOfWeek.SATURDAY, LocalTime.NOON, LocalTime.of(14, 0));
    return new Profile(
        id, "Coyoacan", location, Map.of(), List.of(), List.of(), constraints, Optional.empty());
  }

  private static Context context(Optional<Location> a, Optional<Location> b) {
    Climate unknown = new Climate(Level.UNKNOWN, Level.UNKNOWN);
    return new Context(
        "1-2",
        profile(1, a),
        profile(2, b),
        unknown,
        unknown,
        List.of(SATURDAY),
        Map.of(0, Weather.SUNNY),
        0,
        LocalDate.of(2026, 9, 27));
  }

  private static Context both() {
    return context(Optional.of(ZOCALO), Optional.of(COYOACAN));
  }

  private static PlaceAssignment assign(Context ctx, Catalog cat, Weather w, PlaceParams p) {
    return PlaceAssigner.assign(CITY, ctx, cat, SATURDAY, w, p);
  }

  private static List<String> ids(PlaceAssignment result) {
    return result.options().stream().map(PlaceOption::placeId).toList();
  }

  // U9-01
  @Test
  void givenTwoLocations_whenAssign_thenRadiusIsMeasuredFromTheMidpoint() {
    Catalog cat =
        catalog(indoor("near", 1, false), indoor("north13", 13, false), indoor("far", 17, false));
    PlaceAssignment result = assign(both(), cat, Weather.SUNNY, PARAMS);
    assertThat(result.status()).isEqualTo(PlaceStatus.OK);
    assertThat(ids(result)).containsExactly("near", "north13");
    assertThat(result.relaxations()).isEmpty();
    PlaceOption north = result.options().get(1);
    assertThat(north.distanceBkm().getAsDouble()).isGreaterThan(PARAMS.radiusKm());
    Location at = new Location(MIDPOINT.lat() + 13 / KM_PER_DEGREE_LAT, MIDPOINT.lon());
    assertThat(north.distanceAkm().getAsDouble())
        .isCloseTo(Geo.distanceKm(ZOCALO, at), within(1e-9));
    assertThat(north.hoursUnknown()).isFalse();
  }

  // U9-02
  @Test
  void givenTiesAndMorePlacesThanK_whenAssign_thenOrderedByDistanceVerifiedNameAndCut() {
    Catalog cat =
        catalog(
            indoor("c", 2, false),
            indoor("b", 2, true),
            indoor("a", 2, false),
            indoor("closest", 0, false),
            indoor("fifth", 5, true));
    PlaceAssignment result = assign(both(), cat, Weather.SUNNY, PARAMS);
    assertThat(ids(result)).containsExactly("closest", "b", "a");
    assertThat(result.options().get(1).verified()).isTrue();
  }

  // U9-05
  @Test
  void givenOnlyOneLocation_whenAssign_thenCentredOnTheKnownOneAndOtherDistanceEmpty() {
    Place nearCoyoacan =
        new Place(
            "coy", "Coy", "cinema", COYOACAN.lat(), COYOACAN.lon(), "Coyoacan", OPEN, false, false);
    Place nearZocalo =
        new Place("zoc", "Zoc", "cinema", ZOCALO.lat(), ZOCALO.lon(), "Centro", OPEN, false, false);
    PlaceParams tight = new PlaceParams(5.0, 3, List.of(), false);
    PlaceAssignment result =
        assign(
            context(Optional.empty(), Optional.of(COYOACAN)),
            catalog(nearCoyoacan, nearZocalo),
            Weather.SUNNY,
            tight);
    assertThat(ids(result)).containsExactly("coy");
    assertThat(result.options().get(0).distanceAkm()).isEmpty();
    assertThat(result.options().get(0).distanceBkm().getAsDouble()).isCloseTo(0.0, within(1e-9));
  }

  // U9-05
  @Test
  void givenNoLocations_whenAssign_thenNoRadiusFilterAndVerifiedThenNameOrder() {
    Catalog cat =
        catalog(indoor("zeta", 90, false), indoor("beta", 1, false), indoor("far", 200, true));
    PlaceAssignment result =
        assign(context(Optional.empty(), Optional.empty()), cat, Weather.SUNNY, PARAMS);
    assertThat(ids(result)).containsExactly("far", "beta", "zeta");
    assertThat(result.options()).allSatisfy(o -> assertThat(o.distanceAkm()).isEmpty());
    assertThat(result.options()).allSatisfy(o -> assertThat(o.distanceBkm()).isEmpty());
  }

  // U9-06
  @Test
  void givenRequireVerified_whenAssign_thenUnverifiedPlacesAreIgnored() {
    Catalog cat = catalog(indoor("plain", 1, false), indoor("checked", 3, true));
    PlaceParams verifiedOnly = new PlaceParams(15.0, 3, DEFAULT_RELAX, true);
    PlaceAssignment result = assign(both(), cat, Weather.SUNNY, verifiedOnly);
    assertThat(ids(result)).containsExactly("checked");
    assertThat(ids(assign(both(), cat, Weather.SUNNY, PARAMS))).containsExactly("plain", "checked");
  }

  // U9-06
  @Test
  void givenHomeActivity_whenAssign_thenHomeWithoutOptions() {
    PlaceAssignment result =
        PlaceAssigner.assign(
            HOME, both(), catalog(indoor("p", 1, true)), SATURDAY, Weather.RAINY, PARAMS);
    assertThat(result.status()).isEqualTo(PlaceStatus.HOME);
    assertThat(result.options()).isEmpty();
    assertThat(result.relaxations()).isEmpty();
    assertThat(result.activityId()).isEqualTo(HOME);
  }

  // U9-06
  @Test
  void givenPlaceClosedDuringWindow_whenAssign_thenNeverProposedEvenAfterRelaxing() {
    Place closed = place("closed", 1, Optional.of("Mo-Fr 09:00-18:00"), false, true);
    PlaceAssignment result = assign(both(), catalog(closed), Weather.SUNNY, PARAMS);
    assertThat(result.status()).isEqualTo(PlaceStatus.NO_PLACE);
    assertThat(result.relaxations()).containsExactlyElementsOf(DEFAULT_RELAX);
  }

  // U9-03
  @Test
  void givenOnlyOnePlaceBeyondTheRadius_whenAssign_thenRadiusRelaxedByFiveKmAndStops() {
    PlaceAssignment result =
        assign(both(), catalog(indoor("far", 17, true)), Weather.SUNNY, PARAMS);
    assertThat(result.status()).isEqualTo(PlaceStatus.OK);
    assertThat(ids(result)).containsExactly("far");
    assertThat(result.relaxations()).containsExactly(PlaceRelax.RADIUS);
  }

  // U9-03
  @Test
  void givenOnlyUnknownHours_whenAssign_thenRelaxationsFollowRelaxOrder() {
    Place unknown = place("unknown", 1, Optional.of("Sa off"), false, false);
    Place missing = place("missing", 2, Optional.empty(), false, false);
    PlaceAssignment result = assign(both(), catalog(unknown, missing), Weather.SUNNY, PARAMS);
    assertThat(result.relaxations()).containsExactly(PlaceRelax.RADIUS, PlaceRelax.HOURS_UNKNOWN);
    assertThat(ids(result)).containsExactly("unknown", "missing");
    assertThat(result.options()).allSatisfy(o -> assertThat(o.hoursUnknown()).isTrue());
    PlaceParams hoursFirst =
        new PlaceParams(15.0, 3, List.of(PlaceRelax.HOURS_UNKNOWN, PlaceRelax.RADIUS), false);
    assertThat(assign(both(), catalog(unknown), Weather.SUNNY, hoursFirst).relaxations())
        .containsExactly(PlaceRelax.HOURS_UNKNOWN);
  }

  // U9-03
  @Test
  void givenNothingFits_whenAssign_thenNoPlaceWithEveryRelaxationApplied() {
    PlaceAssignment result =
        assign(both(), catalog(indoor("far", 40, true)), Weather.SUNNY, PARAMS);
    assertThat(result.status()).isEqualTo(PlaceStatus.NO_PLACE);
    assertThat(result.options()).isEmpty();
    assertThat(result.relaxations()).containsExactlyElementsOf(DEFAULT_RELAX);
    PlaceAssignment unknownActivity =
        PlaceAssigner.assign("missing", both(), catalog(), SATURDAY, Weather.SUNNY, PARAMS);
    assertThat(unknownActivity.status()).isEqualTo(PlaceStatus.NO_PLACE);
  }

  // U9-04
  @Test
  void givenRainAndOnlyOutdoorPlaces_whenAssign_thenWeatherIsRelaxed() {
    Place park = place("park", 1, OPEN, true, true);
    PlaceAssignment rainy = assign(both(), catalog(park), Weather.RAINY, PARAMS);
    assertThat(rainy.status()).isEqualTo(PlaceStatus.OK);
    assertThat(rainy.relaxations()).containsExactlyElementsOf(DEFAULT_RELAX);
    PlaceAssignment sunny = assign(both(), catalog(park), Weather.SUNNY, PARAMS);
    assertThat(sunny.relaxations()).isEmpty();
    assertThat(ids(sunny)).containsExactly("park");
  }

  // U9-04
  @Test
  void givenRainWithIndoorAlternative_whenAssign_thenOutdoorExcludedWithoutRelaxing() {
    Place park = place("park", 1, OPEN, true, true);
    Place museum = place("museum", 4, OPEN, false, false);
    PlaceAssignment result = assign(both(), catalog(park, museum), Weather.RAINY, PARAMS);
    assertThat(ids(result)).containsExactly("museum");
    assertThat(result.relaxations()).isEmpty();
  }

  @Test
  void givenLinkToMissingPlaceAndZeroK_whenAssign_thenIgnoredAndNoOptions() {
    Map<String, List<String>> links = Map.of(CITY, List.of("ghost", "real"));
    Place real = indoor("real", 1, true);
    Catalog cat =
        new Catalog(Map.of(CITY, activity(CITY, LocationScope.CITY)), Map.of("real", real), links);
    assertThat(ids(assign(both(), cat, Weather.SUNNY, PARAMS))).containsExactly("real");
    PlaceParams none = new PlaceParams(15.0, 0, List.of(), false);
    assertThat(assign(both(), cat, Weather.SUNNY, none).status()).isEqualTo(PlaceStatus.NO_PLACE);
  }
}
