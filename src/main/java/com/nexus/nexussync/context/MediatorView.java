package com.nexus.nexussync.context;

import com.nexus.nexussync.params.MediatorClimate;
import com.nexus.nexussync.params.Params;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What the mediator agent may see of a couple (unit U3, NOVA adjustment D-10).
 *
 * <p>Keys: {@code couple_id}, {@code today}, {@code windows} (list of {@code {day, start, end,
 * daypart}}), {@code preferences_a}, {@code preferences_b}, {@code climate_a}/{@code climate_b}
 * (only when {@code agents.mediator_climate == AGGREGATED}, as {@code {valence, energy}} text) and
 * {@code history_agg} ({@code n_offered}, {@code n_chosen}, {@code n_rated} and, when there is at
 * least one rating, {@code avg_rating}). Private data never leaves: no emotional entries, no
 * individual ratings, no truth weights, no location and no raw history.
 */
public final class MediatorView {

  private static final double ROUND = 100.0;

  private MediatorView() {}

  /**
   * Builds the mediator view of a context.
   *
   * @param ctx context of the couple
   * @param p parameters; {@code agents.mediatorClimate} and {@code context.historyWindowDays} are
   *     read
   * @return an unmodifiable, insertion-ordered map ready to be serialized to JSON
   * @implNote O(h + k log k) time and O(h + k) space, h history entries and k preferences.
   */
  public static Map<String, Object> of(Context ctx, Params p) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("couple_id", ctx.coupleId());
    view.put("today", ctx.today().toString());
    view.put("windows", windows(ctx.windows()));
    view.put("preferences_a", Collections.unmodifiableMap(new TreeMap<>(ctx.a().preferences())));
    view.put("preferences_b", Collections.unmodifiableMap(new TreeMap<>(ctx.b().preferences())));
    if (p.agents().mediatorClimate() == MediatorClimate.AGGREGATED) {
      view.put("climate_a", climate(ctx.climateA()));
      view.put("climate_b", climate(ctx.climateB()));
    }
    view.put("history_agg", historyAgg(ctx, p.context().historyWindowDays()));
    return Collections.unmodifiableMap(view);
  }

  private static List<Map<String, Object>> windows(List<Window> windows) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Window w : windows) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("day", w.day().name());
      m.put("start", w.start().toString());
      m.put("end", w.end().toString());
      m.put("daypart", w.daypart().name());
      out.add(Collections.unmodifiableMap(m));
    }
    return Collections.unmodifiableList(out);
  }

  private static Map<String, Object> climate(Climate c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("valence", c.valence().name());
    m.put("energy", c.energy().name());
    return Collections.unmodifiableMap(m);
  }

  /**
   * Offers and choices are counted once per {@code (activity_id, date)} across both persons;
   * ratings of both persons are averaged.
   */
  private static Map<String, Object> historyAgg(Context ctx, int days) {
    Set<String> offered = new HashSet<>();
    Set<String> chosen = new HashSet<>();
    List<Integer> ratings = new ArrayList<>();
    for (Profile person : List.of(ctx.a(), ctx.b())) {
      for (HistoryEntry h : person.history()) {
        if (ClimateCalculator.withinDays(h.date(), ctx.today(), days)) {
          collect(h, offered, chosen, ratings);
        }
      }
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("n_offered", offered.size());
    m.put("n_chosen", chosen.size());
    m.put("n_rated", ratings.size());
    if (!ratings.isEmpty()) {
      double sum = 0;
      for (int r : ratings) {
        sum += r;
      }
      m.put("avg_rating", Math.round(sum / ratings.size() * ROUND) / ROUND);
    }
    return Collections.unmodifiableMap(m);
  }

  private static void collect(
      HistoryEntry h, Set<String> offered, Set<String> chosen, List<Integer> ratings) {
    String key = h.activityId() + "|" + h.date();
    if (h.offered()) {
      offered.add(key);
    }
    if (h.chosen()) {
      chosen.add(key);
      h.rating().ifPresent(ratings::add);
    }
  }
}
