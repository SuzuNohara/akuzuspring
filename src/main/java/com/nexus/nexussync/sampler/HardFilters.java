package com.nexus.nexussync.sampler;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.Daypart;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.catalog.Place;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.HistoryEntry;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.context.Weather;
import com.nexus.nexussync.context.Window;
import com.nexus.nexussync.params.FilterName;
import com.nexus.nexussync.params.SamplerParams;
import com.nexus.nexussync.places.Geo;
import com.nexus.nexussync.sampler.Budget.PriceBand;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The eight hard filters of G2 in pipeline order (§3.4, {@code pipeline.md} G2).
 *
 * <ol>
 *   <li>SCOPE: HOME always passes; CITY only when there is at least one window.
 *   <li>BUDGET: {@code cost_mxn_pp <= ceiling(budget)}.
 *   <li>DURATION: {@code duration_min} fits in some window.
 *   <li>DAYPART: the activity dayparts meet the window dayparts; empty means any.
 *   <li>SEASON: {@code seasons} contains ANY or meets {@link Seasons#active(LocalDate)}.
 *   <li>RADIUS: only CITY with a known location; some linked place within the radius of the
 *       midpoint (both known) or of the known location; no location passes.
 *   <li>WEATHER: outdoor, every window RAINY and {@code rain_max < 1} rejects; UNKNOWN passes.
 *   <li>COOLDOWN: offered in the last {@code cooldown_days} and not chosen with rating at least 4.
 * </ol>
 */
public final class HardFilters {

  /** Minimum rating that exempts a recently offered activity from the cooldown. */
  static final int COOLDOWN_EXEMPT_RATING = 4;

  private static final List<HardFilter> ALL =
      List.of(
          HardFilters::scope,
          HardFilters::budget,
          HardFilters::duration,
          HardFilters::daypart,
          HardFilters::season,
          HardFilters::radius,
          HardFilters::weather,
          HardFilters::cooldown);

  private HardFilters() {}

  /**
   * Every hard filter in pipeline order (SCOPE, BUDGET, DURATION, DAYPART, SEASON, RADIUS, WEATHER,
   * COOLDOWN), aligned with {@link FilterName#values()}.
   *
   * @return an unmodifiable list of eight filters
   * @implNote O(1) time and space.
   */
  public static List<HardFilter> all() {
    return ALL;
  }

  static Optional<FilterName> scope(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    boolean ok = a.locationScope() == LocationScope.HOME || !ctx.windows().isEmpty();
    return verdict(ok, FilterName.SCOPE);
  }

  static Optional<FilterName> budget(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    return verdict(a.costMxnPp() <= Budget.ceiling(budget), FilterName.BUDGET);
  }

  static Optional<FilterName> duration(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    boolean ok = ctx.windows().stream().anyMatch(w -> minutes(w) >= a.durationMin());
    return verdict(ok, FilterName.DURATION);
  }

  static Optional<FilterName> daypart(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    Set<Daypart> wanted = a.dayparts();
    boolean ok =
        wanted.isEmpty() || ctx.windows().stream().anyMatch(w -> wanted.contains(w.daypart()));
    return verdict(ok, FilterName.DAYPART);
  }

  static Optional<FilterName> season(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    Set<String> active = Seasons.active(ctx.today());
    boolean ok =
        a.seasons().contains(Seasons.ANY) || a.seasons().stream().anyMatch(active::contains);
    return verdict(ok, FilterName.SEASON);
  }

  static Optional<FilterName> radius(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    if (a.locationScope() != LocationScope.CITY) {
      return Optional.empty();
    }
    Optional<Location> ref = reference(ctx);
    if (ref.isEmpty()) {
      return Optional.empty();
    }
    OptionalDouble d = nearestKm(a, cat, ref.get());
    return verdict(d.isPresent() && d.getAsDouble() <= radiusKm, FilterName.RADIUS);
  }

  static Optional<FilterName> weather(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    boolean allRainy =
        !ctx.windows().isEmpty()
            && IntStream.range(0, ctx.windows().size())
                .allMatch(i -> ctx.weather().getOrDefault(i, Weather.UNKNOWN) == Weather.RAINY);
    return verdict(!(a.outdoor() && allRainy && p.rainMax() < 1.0), FilterName.WEATHER);
  }

  static Optional<FilterName> cooldown(
      Activity a, Context ctx, Catalog cat, SamplerParams p, double radiusKm, PriceBand budget) {
    LocalDate since = ctx.today().minusDays(p.cooldownDays());
    List<HistoryEntry> recent =
        history(ctx)
            .filter(h -> h.activityId().equals(a.activityId()))
            .filter(h -> h.date().isAfter(since) && !h.date().isAfter(ctx.today()))
            .toList();
    boolean offered = recent.stream().anyMatch(HistoryEntry::offered);
    boolean exempt = recent.stream().anyMatch(HardFilters::chosenAndLiked);
    return verdict(!offered || exempt, FilterName.COOLDOWN);
  }

  /**
   * Reference point of the couple: midpoint when both locations are known, the known one when only
   * one is, empty otherwise.
   *
   * @implNote O(1) time and space.
   */
  static Optional<Location> reference(Context ctx) {
    Optional<Location> a = ctx.a().location();
    Optional<Location> b = ctx.b().location();
    if (a.isPresent() && b.isPresent()) {
      return Optional.of(Geo.midpoint(a.get(), b.get()));
    }
    return a.isPresent() ? a : b;
  }

  /**
   * Distance from {@code ref} to the nearest place linked to {@code a}; ignores dangling links.
   *
   * @implNote O(l) time, l = linked places; O(1) extra space.
   */
  static OptionalDouble nearestKm(Activity a, Catalog cat, Location ref) {
    return cat.links().getOrDefault(a.activityId(), List.of()).stream()
        .map(id -> Optional.ofNullable(cat.places().get(id)))
        .flatMap(Optional::stream)
        .mapToDouble(pl -> distance(pl, ref))
        .min();
  }

  /** Both histories of the couple, a first. */
  static Stream<HistoryEntry> history(Context ctx) {
    return Stream.concat(ctx.a().history().stream(), ctx.b().history().stream());
  }

  private static boolean chosenAndLiked(HistoryEntry h) {
    return h.chosen() && h.rating().orElse(0) >= COOLDOWN_EXEMPT_RATING;
  }

  private static double distance(Place pl, Location ref) {
    return Geo.haversineKm(pl.lat(), pl.lon(), ref.lat(), ref.lon());
  }

  private static long minutes(Window w) {
    return Duration.between(w.start(), w.end()).toMinutes();
  }

  private static Optional<FilterName> verdict(boolean passes, FilterName name) {
    return passes ? Optional.empty() : Optional.of(name);
  }
}
