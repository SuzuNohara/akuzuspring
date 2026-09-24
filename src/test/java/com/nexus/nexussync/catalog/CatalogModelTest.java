package com.nexus.nexussync.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests de los records y enums del catálogo (§3.2): inmutabilidad y copias defensivas (D-08). */
class CatalogModelTest {

  private static Activity activity(Set<String> placeTypes, Set<Daypart> dayparts) {
    return new Activity(
        "picnic-en-el-parque",
        "Picnic en el parque",
        "PICNIC",
        LocationScope.CITY,
        placeTypes,
        Set.of("picnics"),
        dayparts,
        Set.of("ANY"),
        90,
        120,
        180,
        1,
        1,
        6,
        3,
        150,
        "LOW",
        true,
        Set.of("SUNNY"),
        Set.of("NATURE", "ROMANTIC"),
        "Comer en el pasto.");
  }

  // U2-02
  @Test
  void given_location_scope_and_daypart_enums_when_listed_then_values_of_the_schema() {
    assertThat(LocationScope.values()).containsExactly(LocationScope.HOME, LocationScope.CITY);
    assertThat(Daypart.values()).containsExactly(Daypart.MORNING, Daypart.AFTERNOON, Daypart.NIGHT);
  }

  // U2-02
  @Test
  void given_mutable_sets_when_activity_built_then_sets_are_copied_and_unmodifiable() {
    Set<String> placeTypes = new HashSet<>(Set.of("PARK"));
    Set<Daypart> dayparts = new HashSet<>(Set.of(Daypart.AFTERNOON));

    Activity activity = activity(placeTypes, dayparts);
    placeTypes.add("GARDEN");
    dayparts.add(Daypart.NIGHT);

    assertThat(activity.placeTypes()).containsExactly("PARK");
    assertThat(activity.dayparts()).containsExactly(Daypart.AFTERNOON);
    assertThatThrownBy(() -> activity.placeTypes().add("LAKE"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> activity.interests().add("leer"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // U2-02
  @Test
  void given_activity_when_read_then_scalar_components_are_kept() {
    Activity activity = activity(Set.of("PARK"), Set.of(Daypart.AFTERNOON));

    assertThat(activity.activityId()).isEqualTo("picnic-en-el-parque");
    assertThat(activity.locationScope()).isEqualTo(LocationScope.CITY);
    assertThat(activity.durationMin()).isEqualTo(90);
    assertThat(activity.costMxnPp()).isEqualTo(150);
    assertThat(activity.priceBand()).isEqualTo("LOW");
    assertThat(activity.outdoor()).isTrue();
    assertThat(activity.weatherOk()).containsExactly("SUNNY");
    assertThat(activity.ambience()).containsExactlyInAnyOrder("NATURE", "ROMANTIC");
  }

  // U2-02
  @Test
  void given_place_when_built_then_optional_hours_and_flags_are_kept() {
    Place open =
        new Place(
            "parque-uno",
            "Parque Uno",
            "PARK",
            19.42,
            -99.16,
            "Cuauhtémoc",
            Optional.of("Mo-Su 06:00-20:00"),
            true,
            true);
    Place unknown =
        new Place(
            "museo-uno",
            "Museo Uno",
            "MUSEUM",
            19.45,
            -99.19,
            "Cuauhtémoc",
            Optional.empty(),
            false,
            false);

    assertThat(open.openingHours()).contains("Mo-Su 06:00-20:00");
    assertThat(open.outdoor()).isTrue();
    assertThat(open.verified()).isTrue();
    assertThat(unknown.openingHours()).isEmpty();
    assertThat(unknown.lat()).isEqualTo(19.45);
    assertThat(unknown.lon()).isEqualTo(-99.19);
  }

  // U2-02
  @Test
  void given_link_when_built_then_cost_override_is_optional() {
    Link priced = new Link("picnic-en-el-parque", "parque-uno", OptionalInt.of(50));
    Link plain = new Link("picnic-en-el-parque", "parque-dos", OptionalInt.empty());

    assertThat(priced.costOverride()).hasValue(50);
    assertThat(plain.costOverride()).isEmpty();
    assertThat(plain.placeId()).isEqualTo("parque-dos");
  }

  // U2-02
  @Test
  void given_mutable_maps_when_catalog_built_then_maps_and_lists_are_copied() {
    Map<String, Activity> activities = new HashMap<>();
    activities.put("a", activity(Set.of("PARK"), Set.of(Daypart.AFTERNOON)));
    Map<String, Place> places = new HashMap<>();
    List<String> targets = new ArrayList<>(List.of("parque-uno"));
    Map<String, List<String>> links = new HashMap<>();
    links.put("a", targets);

    final Catalog catalog = new Catalog(activities, places, links);
    activities.clear();
    targets.add("parque-dos");
    links.put("b", List.of());

    assertThat(catalog.activities()).containsOnlyKeys("a");
    assertThat(catalog.places()).isEmpty();
    assertThat(catalog.links()).containsOnlyKeys("a");
    assertThat(catalog.links().get("a")).containsExactly("parque-uno");
    assertThatThrownBy(() -> catalog.links().get("a").add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> catalog.activities().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
