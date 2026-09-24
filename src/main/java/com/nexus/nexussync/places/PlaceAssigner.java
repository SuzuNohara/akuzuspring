package com.nexus.nexussync.places;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.catalog.Place;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.context.Weather;
import com.nexus.nexussync.context.Window;
import com.nexus.nexussync.params.PlaceParams;
import com.nexus.nexussync.params.PlaceRelax;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

/**
 * Assigns places to an activity around the couple's midpoint (§3.9, pipeline G5).
 *
 * <p>Centre: midpoint of both locations, the known one if only one person has a location, none (no
 * radius filter) if neither has. Eligible places are the activity's linked places that are inside
 * the radius, not known to be closed during the window, with known hours and, when it rains,
 * indoors. While no place is eligible the constraints are relaxed in {@code relaxOrder}: {@code
 * RADIUS} adds 5 km, {@code HOURS_UNKNOWN} accepts places with unknown hours, {@code WEATHER}
 * accepts outdoor places under rain. Options are ordered by mean distance ascending, then verified
 * first, then name.
 */
public final class PlaceAssigner {

  /** Kilometres added to the radius by each {@code RADIUS} relaxation. */
  static final double RADIUS_STEP_KM = 5.0;

  private static final Comparator<Candidate> ORDER =
      Comparator.comparingDouble(Candidate::meanKm)
          .thenComparing(c -> !c.option().verified())
          .thenComparing(c -> c.place().name())
          .thenComparing(c -> c.place().placeId());

  private PlaceAssigner() {}

  /** A linked place with its distances and hours already evaluated. */
  private record Candidate(
      Place place, PlaceOption option, OptionalDouble centerKm, double meanKm, boolean closed) {}

  /** Current state of the relaxable constraints. */
  private record Limits(double radiusKm, boolean unknownHoursOk, boolean rainOk) {

    Limits relax(PlaceRelax step) {
      return switch (step) {
        case RADIUS -> new Limits(radiusKm + RADIUS_STEP_KM, unknownHoursOk, rainOk);
        case HOURS_UNKNOWN -> new Limits(radiusKm, true, rainOk);
        case WEATHER -> new Limits(radiusKm, unknownHoursOk, true);
      };
    }
  }

  /**
   * Proposes up to {@code placesK} places for an activity in a window.
   *
   * @param activityId slug of the activity
   * @param ctx context of the couple (locations of both persons)
   * @param cat catalog with the activity, its linked places and the places
   * @param w window in which the activity would happen
   * @param weather forecast for the window
   * @param p place parameters
   * @return {@code HOME} for home activities, {@code OK} with ordered options, or {@code NO_PLACE}
   *     with every relaxation applied
   * @implNote O(r · n log n) time and O(n) space, n = linked places, r = relaxation steps.
   */
  public static PlaceAssignment assign(
      String activityId, Context ctx, Catalog cat, Window w, Weather weather, PlaceParams p) {
    Optional<Activity> activity = Optional.ofNullable(cat.activities().get(activityId));
    if (activity.map(a -> a.locationScope() == LocationScope.HOME).orElse(false)) {
      return new PlaceAssignment(activityId, PlaceStatus.HOME, List.of(), List.of());
    }
    List<Candidate> candidates = candidates(activityId, ctx, cat, w, p.requireVerified());
    boolean rain = weather == Weather.RAINY;
    Limits limits = new Limits(p.radiusKm(), false, false);
    List<PlaceRelax> applied = new ArrayList<>();
    List<PlaceOption> options = select(candidates, limits, rain, p.placesK());
    for (PlaceRelax step : p.relaxOrder()) {
      if (!options.isEmpty()) {
        break;
      }
      limits = limits.relax(step);
      applied.add(step);
      options = select(candidates, limits, rain, p.placesK());
    }
    PlaceStatus status = options.isEmpty() ? PlaceStatus.NO_PLACE : PlaceStatus.OK;
    return new PlaceAssignment(activityId, status, options, applied);
  }

  private static List<PlaceOption> select(
      List<Candidate> candidates, Limits limits, boolean rain, int placesK) {
    return candidates.stream()
        .filter(c -> accepts(c, limits, rain))
        .sorted(ORDER)
        .limit(Math.max(0, placesK))
        .map(Candidate::option)
        .toList();
  }

  private static boolean accepts(Candidate c, Limits limits, boolean rain) {
    boolean inRadius = c.centerKm().isEmpty() || c.centerKm().getAsDouble() <= limits.radiusKm();
    boolean hoursOk = !c.option().hoursUnknown() || limits.unknownHoursOk();
    boolean weatherOk = !rain || !c.place().outdoor() || limits.rainOk();
    return !c.closed() && inRadius && hoursOk && weatherOk;
  }

  private static List<Candidate> candidates(
      String activityId, Context ctx, Catalog cat, Window w, boolean requireVerified) {
    Optional<Location> a = ctx.a().location();
    Optional<Location> b = ctx.b().location();
    Optional<Location> center = center(a, b);
    return cat.links().getOrDefault(activityId, List.of()).stream()
        .map(cat.places()::get)
        .filter(Objects::nonNull)
        .filter(place -> !requireVerified || place.verified())
        .map(place -> candidate(place, a, b, center, w))
        .toList();
  }

  private static Optional<Location> center(Optional<Location> a, Optional<Location> b) {
    if (a.isPresent() && b.isPresent()) {
      return Optional.of(Geo.midpoint(a.get(), b.get()));
    }
    return a.isPresent() ? a : b;
  }

  private static Candidate candidate(
      Place place,
      Optional<Location> a,
      Optional<Location> b,
      Optional<Location> center,
      Window w) {
    Location at = new Location(place.lat(), place.lon());
    OptionalDouble distanceA = distance(a, at);
    OptionalDouble distanceB = distance(b, at);
    Optional<Boolean> open = place.openingHours().flatMap(hours -> OpeningHours.covers(hours, w));
    PlaceOption option =
        new PlaceOption(place.placeId(), distanceA, distanceB, open.isEmpty(), place.verified());
    double mean = DoubleStream.concat(distanceA.stream(), distanceB.stream()).average().orElse(0.0);
    boolean closed = open.map(isOpen -> !isOpen).orElse(false);
    return new Candidate(place, option, distance(center, at), mean, closed);
  }

  private static OptionalDouble distance(Optional<Location> from, Location to) {
    return from.map(f -> OptionalDouble.of(Geo.distanceKm(f, to))).orElse(OptionalDouble.empty());
  }
}
