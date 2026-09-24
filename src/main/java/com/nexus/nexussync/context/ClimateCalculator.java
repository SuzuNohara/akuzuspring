package com.nexus.nexussync.context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregated emotional climate of a person (unit U3, NOVA adjustment D-09).
 *
 * <p>Each valid entry inside the window contributes its valence and energy (fixed tables below),
 * weighted by its intensity. The weighted mean maps to {@code LOW} below -0.33, {@code HIGH} above
 * 0.33 and {@code MID} otherwise; with no valid entry both axes are {@code UNKNOWN}. The window
 * includes {@code today - windowDays} and {@code today} and excludes any other day. An entry with
 * an unknown code is ignored and counted ({@link #unknownCodes}); an entry with an intensity
 * outside 1–5 is ignored.
 */
public final class ClimateCalculator {

  private static final Logger LOG = LoggerFactory.getLogger(ClimateCalculator.class);
  private static final double THRESHOLD = 0.33;
  private static final int INTENSITY_MIN = 1;
  private static final int INTENSITY_MAX = 5;
  private static final EnumMap<EmotionCode, Double> VALENCE = valenceTable();
  private static final EnumMap<EmotionCode, Double> ENERGY = energyTable();

  private ClimateCalculator() {}

  /**
   * Computes the climate of a person.
   *
   * @param entries recent emotional records of the person
   * @param today reference day
   * @param windowDays days before {@code today} that are considered
   * @return levels of valence and energy, {@code UNKNOWN} when no entry is valid
   * @implNote O(n) time and space in the number of entries.
   */
  public static Climate of(List<EmotionEntry> entries, LocalDate today, int windowDays) {
    int unknown = unknownCodes(entries, today, windowDays);
    if (unknown > 0) {
      LOG.warn("clima: {} entradas con código desconocido ignoradas", unknown);
    }
    List<Weighted> valid = valid(entries, today, windowDays);
    if (valid.isEmpty()) {
      return new Climate(Level.UNKNOWN, Level.UNKNOWN);
    }
    return new Climate(levelOf(mean(valid, VALENCE)), levelOf(mean(valid, ENERGY)));
  }

  /**
   * Counts the entries inside the window whose code is not an {@link EmotionCode}.
   *
   * @param entries recent emotional records of the person
   * @param today reference day
   * @param windowDays days before {@code today} that are considered
   * @return number of ignored entries because of their code
   * @implNote O(n) time and O(1) space in the number of entries.
   */
  public static int unknownCodes(List<EmotionEntry> entries, LocalDate today, int windowDays) {
    int count = 0;
    for (EmotionEntry e : entries) {
      if (withinDays(e.date(), today, windowDays) && EmotionCode.parse(e.code()).isEmpty()) {
        count++;
      }
    }
    return count;
  }

  static OptionalDouble meanValence(List<EmotionEntry> entries, LocalDate today, int windowDays) {
    List<Weighted> valid = valid(entries, today, windowDays);
    return valid.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(mean(valid, VALENCE));
  }

  static OptionalDouble meanEnergy(List<EmotionEntry> entries, LocalDate today, int windowDays) {
    List<Weighted> valid = valid(entries, today, windowDays);
    return valid.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(mean(valid, ENERGY));
  }

  static Level levelOf(double mean) {
    if (mean < -THRESHOLD) {
      return Level.LOW;
    }
    if (mean > THRESHOLD) {
      return Level.HIGH;
    }
    return Level.MID;
  }

  static boolean withinDays(LocalDate date, LocalDate today, int days) {
    return !date.isAfter(today) && !date.isBefore(today.minusDays(days));
  }

  private static List<Weighted> valid(List<EmotionEntry> entries, LocalDate today, int days) {
    List<Weighted> out = new ArrayList<>();
    for (EmotionEntry e : entries) {
      boolean inRange = e.intensity() >= INTENSITY_MIN && e.intensity() <= INTENSITY_MAX;
      if (inRange && withinDays(e.date(), today, days)) {
        EmotionCode.parse(e.code()).ifPresent(c -> out.add(new Weighted(c, e.intensity())));
      }
    }
    return out;
  }

  private static double mean(List<Weighted> valid, EnumMap<EmotionCode, Double> table) {
    double sum = 0;
    double weight = 0;
    for (Weighted w : valid) {
      sum += w.intensity() * table.getOrDefault(w.code(), 0.0);
      weight += w.intensity();
    }
    return sum / weight;
  }

  private static EnumMap<EmotionCode, Double> valenceTable() {
    EnumMap<EmotionCode, Double> t = new EnumMap<>(EmotionCode.class);
    t.put(EmotionCode.HAPPY, 1.0);
    t.put(EmotionCode.GRATEFUL, 0.8);
    t.put(EmotionCode.CALM, 0.5);
    t.put(EmotionCode.NEUTRAL, 0.0);
    t.put(EmotionCode.TIRED, -0.4);
    t.put(EmotionCode.STRESSED, -0.6);
    t.put(EmotionCode.ANGRY, -0.8);
    t.put(EmotionCode.SAD, -1.0);
    return t;
  }

  private static EnumMap<EmotionCode, Double> energyTable() {
    EnumMap<EmotionCode, Double> t = new EnumMap<>(EmotionCode.class);
    t.put(EmotionCode.ANGRY, 0.9);
    t.put(EmotionCode.STRESSED, 0.7);
    t.put(EmotionCode.HAPPY, 0.6);
    t.put(EmotionCode.GRATEFUL, 0.2);
    t.put(EmotionCode.NEUTRAL, 0.0);
    t.put(EmotionCode.CALM, -0.6);
    t.put(EmotionCode.SAD, -0.6);
    t.put(EmotionCode.TIRED, -1.0);
    return t;
  }

  private record Weighted(EmotionCode code, int intensity) {}
}
