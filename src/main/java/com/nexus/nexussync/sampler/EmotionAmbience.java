package com.nexus.nexussync.sampler;

import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Level;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Emotional grid of the sampler: preferred {@code ambience} values per valence x energy cell (unit
 * U4, investigation 2026-09-23 "Grid de modulación por clima emocional").
 *
 * <p>Ambience values are those of the {@code ambience} enum of {@code kb/schema.md} §3: ROMANTIC,
 * QUIET, CULTURAL, ACTIVE, SOCIAL, NATURE, HOME. Cells that the investigation does not map to an
 * explicit ambience set are fixed by NOVA with the rule "high energy: active/social, low energy:
 * calm/intimate, low valence: comforting":
 *
 * <table>
 *   <caption>Grid (valence rows, energy columns)</caption>
 *   <tr><th></th><th>LOW</th><th>MID</th><th>HIGH</th></tr>
 *   <tr><td>LOW</td><td>QUIET, NATURE, HOME</td><td>NATURE, QUIET (fixed)</td>
 *       <td>ACTIVE, NATURE (fixed)</td></tr>
 *   <tr><td>MID</td><td>QUIET, ROMANTIC, HOME</td><td>all (default profile)</td>
 *       <td>ACTIVE, CULTURAL</td></tr>
 *   <tr><td>HIGH</td><td>ROMANTIC, QUIET</td><td>ACTIVE, CULTURAL, SOCIAL (fixed)</td>
 *       <td>ACTIVE, CULTURAL, SOCIAL, NATURE (fixed)</td></tr>
 * </table>
 *
 * <p>{@link Level#UNKNOWN} on either axis yields every ambience.
 */
public final class EmotionAmbience {

  /** {@code ambience} value ROMANTIC. */
  public static final String ROMANTIC = "ROMANTIC";

  /** {@code ambience} value QUIET. */
  public static final String QUIET = "QUIET";

  /** {@code ambience} value CULTURAL. */
  public static final String CULTURAL = "CULTURAL";

  /** {@code ambience} value ACTIVE. */
  public static final String ACTIVE = "ACTIVE";

  /** {@code ambience} value SOCIAL. */
  public static final String SOCIAL = "SOCIAL";

  /** {@code ambience} value NATURE. */
  public static final String NATURE = "NATURE";

  /** {@code ambience} value HOME. */
  public static final String HOME = "HOME";

  /** Every value of the {@code ambience} enum. */
  public static final Set<String> ALL =
      Set.of(ROMANTIC, QUIET, CULTURAL, ACTIVE, SOCIAL, NATURE, HOME);

  private static final Map<Level, Map<Level, Set<String>>> GRID = grid();

  private EmotionAmbience() {}

  /**
   * Preferred ambience values for a climate.
   *
   * @param c climate of one person
   * @return an unmodifiable set; {@link #ALL} when either axis is {@link Level#UNKNOWN}
   * @implNote O(1) time and space (fixed 3x3 grid).
   */
  public static Set<String> preferred(Climate c) {
    Objects.requireNonNull(c, "climate");
    if (c.valence() == Level.UNKNOWN || c.energy() == Level.UNKNOWN) {
      return ALL;
    }
    return GRID.get(c.valence()).get(c.energy());
  }

  private static Map<Level, Map<Level, Set<String>>> grid() {
    Map<Level, Map<Level, Set<String>>> g = new EnumMap<>(Level.class);
    g.put(
        Level.LOW, row(Set.of(QUIET, NATURE, HOME), Set.of(NATURE, QUIET), Set.of(ACTIVE, NATURE)));
    g.put(Level.MID, row(Set.of(QUIET, ROMANTIC, HOME), ALL, Set.of(ACTIVE, CULTURAL)));
    g.put(
        Level.HIGH,
        row(
            Set.of(ROMANTIC, QUIET),
            Set.of(ACTIVE, CULTURAL, SOCIAL),
            Set.of(ACTIVE, CULTURAL, SOCIAL, NATURE)));
    return g;
  }

  private static Map<Level, Set<String>> row(Set<String> low, Set<String> mid, Set<String> high) {
    Map<Level, Set<String>> r = new EnumMap<>(Level.class);
    r.put(Level.LOW, low);
    r.put(Level.MID, mid);
    r.put(Level.HIGH, high);
    return r;
  }
}
