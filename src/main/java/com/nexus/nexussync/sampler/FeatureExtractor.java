package com.nexus.nexussync.sampler;

import com.nexus.nexussync.catalog.Activity;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.catalog.LocationScope;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.HistoryEntry;
import com.nexus.nexussync.context.Level;
import com.nexus.nexussync.context.Location;
import com.nexus.nexussync.context.Profile;
import com.nexus.nexussync.params.Feature;
import com.nexus.nexussync.params.SamplerParams;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Feature vector of an activity for a couple (§3.4); every value lies in {@code [0, 1]} (EMOTION is
 * additionally multiplied by {@code emotionWeight}).
 *
 * <ul>
 *   <li>INTEREST: mean over the activity interests known to at least one person of {@code ((lvlA -
 *       1) / 4 + (lvlB - 1) / 4) / 2}; a level missing for one person counts as the neutral {@value
 *       #NEUTRAL_LEVEL}; halved when any level is 1; 0 when no interest is known.
 *   <li>PRICE: {@code 1 - cost / ceiling(couple budget)}; a zero ceiling gives 1 for a free
 *       activity and 0 otherwise.
 *   <li>DISTANCE: {@code 1 - dMin / radius} from the reference point; 0.5 without any location; 1
 *       for HOME; 0 for CITY without a reachable linked place.
 *   <li>NOVELTY: 1 never offered, 0.3 offered and never chosen, 0 chosen (history of both).
 *   <li>EMOTION: share of the activity ambience in the preferred ambience of both climates, times
 *       {@code emotionWeight}; 0.5 when disabled, when both climates are unknown or when the
 *       activity has no ambience.
 *   <li>COLLAB: {@code 1 - |collaboration - target| / 10}; target = mean collaboration of the
 *       chosen activities found in the catalog, 5 when there are none.
 *   <li>SEASON: 1 when a season of the activity is active today, 0.5 for ANY only, 0 otherwise.
 * </ul>
 */
public final class FeatureExtractor {

  /** Level assumed for an interest one person did not rate. */
  static final int NEUTRAL_LEVEL = 3;

  /** Lowest preference level; its presence halves INTEREST. */
  static final int MIN_LEVEL = 1;

  /** Span of the preference levels (5 - 1). */
  static final double LEVEL_SPAN = 4.0;

  /** Neutral value of a feature that cannot be computed. */
  static final double NEUTRAL = 0.5;

  /** NOVELTY of an activity offered but never chosen. */
  static final double OFFERED_NOVELTY = 0.3;

  /** Collaboration target without any chosen activity. */
  static final double DEFAULT_COLLAB = 5.0;

  /** Width of the collaboration scale. */
  static final double COLLAB_SPAN = 10.0;

  private FeatureExtractor() {}

  /**
   * Computes the seven features of {@code a} for the couple of {@code ctx}.
   *
   * @param a activity to describe
   * @param ctx context of the couple
   * @param cat catalog, for linked places and the collaboration of chosen activities
   * @param p sampler parameters (radius, emotion switch and weight)
   * @return an unmodifiable map with every {@link Feature}
   * @implNote O(i + h + l) time, i = interests, h = history entries, l = linked places; O(h) space.
   */
  public static Map<Feature, Double> of(Activity a, Context ctx, Catalog cat, SamplerParams p) {
    Map<Feature, Double> x = new EnumMap<>(Feature.class);
    x.put(Feature.INTEREST, interest(a, ctx.a(), ctx.b()));
    x.put(Feature.PRICE, price(a, ctx));
    x.put(Feature.DISTANCE, distance(a, ctx, cat, p.radiusKm()));
    x.put(Feature.NOVELTY, novelty(a, ctx));
    x.put(Feature.EMOTION, emotion(a, ctx, p));
    x.put(Feature.COLLAB, collab(a, ctx, cat));
    x.put(Feature.SEASON, season(a, ctx));
    return Map.copyOf(x);
  }

  static double interest(Activity a, Profile pa, Profile pb) {
    double sum = 0.0;
    int known = 0;
    boolean disliked = false;
    for (String slug : a.interests()) {
      Integer la = pa.preferences().get(slug);
      Integer lb = pb.preferences().get(slug);
      if (la == null && lb == null) {
        continue;
      }
      int x = la == null ? NEUTRAL_LEVEL : la;
      int y = lb == null ? NEUTRAL_LEVEL : lb;
      disliked |= x == MIN_LEVEL || y == MIN_LEVEL;
      sum += ((x - MIN_LEVEL) / LEVEL_SPAN + (y - MIN_LEVEL) / LEVEL_SPAN) / 2.0;
      known++;
    }
    if (known == 0) {
      return 0.0;
    }
    double mean = sum / known;
    return disliked ? mean * NEUTRAL : mean;
  }

  static double price(Activity a, Context ctx) {
    int ceiling =
        Budget.ceiling(
            Budget.lowest(ctx.a().constraints().budgetBand(), ctx.b().constraints().budgetBand()));
    if (ceiling == 0) {
      return a.costMxnPp() == 0 ? 1.0 : 0.0;
    }
    return clamp(1.0 - (double) a.costMxnPp() / ceiling);
  }

  static double distance(Activity a, Context ctx, Catalog cat, double radiusKm) {
    if (a.locationScope() == LocationScope.HOME) {
      return 1.0;
    }
    Optional<Location> ref = HardFilters.reference(ctx);
    if (ref.isEmpty()) {
      return NEUTRAL;
    }
    OptionalDouble d = HardFilters.nearestKm(a, cat, ref.get());
    if (d.isEmpty() || radiusKm <= 0.0) {
      return 0.0;
    }
    return clamp(1.0 - d.getAsDouble() / radiusKm);
  }

  static double novelty(Activity a, Context ctx) {
    List<HistoryEntry> mine =
        HardFilters.history(ctx).filter(h -> h.activityId().equals(a.activityId())).toList();
    if (mine.stream().anyMatch(HistoryEntry::chosen)) {
      return 0.0;
    }
    return mine.stream().anyMatch(HistoryEntry::offered) ? OFFERED_NOVELTY : 1.0;
  }

  static double emotion(Activity a, Context ctx, SamplerParams p) {
    if (!p.emotionEnabled()
        || unknown(ctx.climateA().valence(), ctx.climateA().energy())
            && unknown(ctx.climateB().valence(), ctx.climateB().energy())
        || a.ambience().isEmpty()) {
      return NEUTRAL;
    }
    Set<String> preferred = new HashSet<>(EmotionAmbience.preferred(ctx.climateA()));
    preferred.addAll(EmotionAmbience.preferred(ctx.climateB()));
    long hits = a.ambience().stream().filter(preferred::contains).count();
    return (double) hits / a.ambience().size() * p.emotionWeight();
  }

  static double collab(Activity a, Context ctx, Catalog cat) {
    OptionalDouble mean =
        HardFilters.history(ctx)
            .filter(HistoryEntry::chosen)
            .map(h -> Optional.ofNullable(cat.activities().get(h.activityId())))
            .flatMap(Optional::stream)
            .mapToInt(Activity::collaboration)
            .average();
    double target = mean.orElse(DEFAULT_COLLAB);
    return clamp(1.0 - Math.abs(a.collaboration() - target) / COLLAB_SPAN);
  }

  static double season(Activity a, Context ctx) {
    Set<String> active = Seasons.active(ctx.today());
    if (a.seasons().stream().anyMatch(active::contains)) {
      return 1.0;
    }
    return a.seasons().contains(Seasons.ANY) ? NEUTRAL : 0.0;
  }

  private static boolean unknown(Level valence, Level energy) {
    return valence == Level.UNKNOWN || energy == Level.UNKNOWN;
  }

  private static double clamp(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }
}
