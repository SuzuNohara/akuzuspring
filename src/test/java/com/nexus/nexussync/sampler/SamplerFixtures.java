package com.nexus.nexussync.sampler;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.Daypart;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.catalog.Place;
import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Constraints;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.HistoryEntry;
import com.nexus.nexussync.context.Level;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.context.Weather;
import com.nexus.nexussync.context.Window;
import com.nexus.nexussync.params.ExplorationMethod;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.params.LearningMethod;
import com.nexus.nexussync.params.SamplerParams;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Builders shared by the sampler tests; every value is explicit and deterministic. */
final class SamplerFixtures {

  /** Reference day: 2026-09-27 (SUMMER is over, RAINY active). */
  static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

  /** Zócalo, CDMX. */
  static final Location CENTRO = new Location(19.4326, -99.1332);

  private SamplerFixtures() {}

  /** Mutable builder of an {@link Activity} with neutral defaults. */
  static final class Act {
    String id;
    String type = "GAME";
    LocationScope scope = LocationScope.HOME;
    Set<String> interests = Set.of();
    Set<Daypart> dayparts = Set.of();
    Set<String> seasons = Set.of(Seasons.ANY);
    int durationMin = 60;
    int collaboration = 5;
    int cost;
    boolean outdoor;
    Set<String> ambience = Set.of();

    Act(String id) {
      this.id = id;
    }

    Act type(String t) {
      type = t;
      return this;
    }

    Act city() {
      scope = LocationScope.CITY;
      return this;
    }

    Act interests(String... s) {
      interests = Set.of(s);
      return this;
    }

    Act dayparts(Daypart... d) {
      dayparts = Set.of(d);
      return this;
    }

    Act seasons(String... s) {
      seasons = Set.of(s);
      return this;
    }

    Act duration(int m) {
      durationMin = m;
      return this;
    }

    Act collab(int c) {
      collaboration = c;
      return this;
    }

    Act cost(int c) {
      cost = c;
      return this;
    }

    Act outdoor() {
      outdoor = true;
      return this;
    }

    Act ambience(String... s) {
      ambience = Set.of(s);
      return this;
    }

    Activity build() {
      return new Activity(
          id,
          id,
          type,
          scope,
          Set.of(),
          interests,
          dayparts,
          seasons,
          durationMin,
          durationMin,
          durationMin,
          0,
          0,
          collaboration,
          0,
          cost,
          "FREE",
          outdoor,
          Set.of(),
          ambience,
          "");
    }
  }

  static Act act(String id) {
    return new Act(id);
  }

  /** Mutable builder of a {@link Context}. */
  static final class Ctx {
    Optional<Location> locA = Optional.empty();
    Optional<Location> locB = Optional.empty();
    Map<String, Integer> prefA = Map.of();
    Map<String, Integer> prefB = Map.of();
    List<HistoryEntry> histA = new ArrayList<>();
    List<HistoryEntry> histB = new ArrayList<>();
    String budgetA = "PREMIUM";
    String budgetB = "PREMIUM";
    Climate climA = new Climate(Level.UNKNOWN, Level.UNKNOWN);
    Climate climB = new Climate(Level.UNKNOWN, Level.UNKNOWN);
    List<Window> windows = new ArrayList<>(List.of(window(LocalTime.of(10, 0), 180)));
    Map<Integer, Weather> weather = new HashMap<>();
    int rated;
    LocalDate today = TODAY;

    Ctx locations(Optional<Location> a, Optional<Location> b) {
      locA = a;
      locB = b;
      return this;
    }

    Ctx prefs(Map<String, Integer> a, Map<String, Integer> b) {
      prefA = a;
      prefB = b;
      return this;
    }

    Ctx historyA(HistoryEntry h) {
      histA.add(h);
      return this;
    }

    Ctx historyB(HistoryEntry h) {
      histB.add(h);
      return this;
    }

    Ctx budgets(String a, String b) {
      budgetA = a;
      budgetB = b;
      return this;
    }

    Ctx climates(Climate a, Climate b) {
      climA = a;
      climB = b;
      return this;
    }

    Ctx windows(Window... w) {
      windows = new ArrayList<>(List.of(w));
      return this;
    }

    Ctx weather(int i, Weather w) {
      weather.put(i, w);
      return this;
    }

    Ctx rated(int n) {
      rated = n;
      return this;
    }

    Ctx today(LocalDate d) {
      today = d;
      return this;
    }

    Context build() {
      return new Context(
          "1-2",
          profile(1, locA, prefA, histA, budgetA),
          profile(2, locB, prefB, histB, budgetB),
          climA,
          climB,
          windows,
          weather,
          rated,
          today);
    }
  }

  static Ctx ctx() {
    return new Ctx();
  }

  static Window window(LocalTime start, int minutes) {
    Daypart d =
        start.getHour() < 12
            ? Daypart.MORNING
            : start.getHour() < 19 ? Daypart.AFTERNOON : Daypart.NIGHT;
    return new Window(DayOfWeek.SATURDAY, start, start.plusMinutes(minutes), d);
  }

  static HistoryEntry offered(String id, LocalDate date) {
    return new HistoryEntry(id, date, true, false, OptionalInt.empty());
  }

  static HistoryEntry chosen(String id, LocalDate date, int rating) {
    return new HistoryEntry(id, date, true, true, OptionalInt.of(rating));
  }

  private static Profile profile(
      int id,
      Optional<Location> loc,
      Map<String, Integer> prefs,
      List<HistoryEntry> hist,
      String budget) {
    Constraints c =
        new Constraints(
            budget, "MID", DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(22, 0));
    return new Profile(id, "Cuauhtémoc", loc, prefs, List.of(), hist, c, Optional.empty());
  }

  static Place place(String id, Location at) {
    return new Place(
        id, id, "PARK", at.lat(), at.lon(), "Cuauhtémoc", Optional.empty(), true, true);
  }

  static Catalog catalog(List<Activity> acts, List<Place> places, Map<String, List<String>> links) {
    Map<String, Activity> am = new HashMap<>();
    acts.forEach(a -> am.put(a.activityId(), a));
    Map<String, Place> pm = new HashMap<>();
    places.forEach(p -> pm.put(p.placeId(), p));
    return new Catalog(am, pm, links);
  }

  static Catalog catalog(Activity... acts) {
    return catalog(List.of(acts), List.of(), Map.of());
  }

  static Map<Feature, Double> uniformWeights() {
    Map<Feature, Double> w = new EnumMap<>(Feature.class);
    for (Feature f : Feature.values()) {
      w.put(f, 1.0);
    }
    return w;
  }

  /** Builder of {@link SamplerParams} over the {@code default.yml} values. */
  static final class Params {
    int size = 40;
    int min = 15;
    double radius = 15.0;
    int cooldown = 30;
    int maxPerType = 2;
    double homeShare = 0.25;
    double rainMax = 0.6;
    ExplorationMethod exploration = ExplorationMethod.EPS_GREEDY;
    double eps0 = 0.35;
    double tau = 5.0;
    boolean emotion = true;
    double emotionWeight = 1.0;
    List<FilterName> relax =
        List.of(FilterName.COOLDOWN, FilterName.WEATHER, FilterName.BUDGET, FilterName.RADIUS);
    int maxRelax = 4;

    SamplerParams build() {
      return new SamplerParams(
          size,
          min,
          radius,
          cooldown,
          maxPerType,
          homeShare,
          rainMax,
          uniformWeights(),
          LearningMethod.MULTIPLICATIVE,
          0.3,
          exploration,
          eps0,
          tau,
          emotion,
          emotionWeight,
          relax,
          maxRelax);
    }
  }

  static Params params() {
    return new Params();
  }
}
