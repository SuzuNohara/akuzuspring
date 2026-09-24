package com.nexus.nexussync.context;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.nexussync.params.Feature;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Loads profiles and couple files from JSON (unit U3).
 *
 * <p>JSON keys are snake_case and unknown keys are rejected. Optional keys: {@code location},
 * {@code truth_weights}, {@code preferences}, {@code emotional_recent}, {@code history} (the last
 * three default to empty) and {@code rating} of a history entry. Every level, intensity and rating
 * must be in 1–5 and the window must not cross midnight ({@code window_end > window_start}).
 */
public final class ProfileLoader {

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new Jdk8Module())
          .addModule(new JavaTimeModule())
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  private static final Set<String> BUDGET_BANDS = Set.of("FREE", "LOW", "MID", "HIGH", "PREMIUM");
  private static final Set<String> TRAVEL_BANDS = Set.of("NEAR", "MID", "FAR");
  private static final int SCALE_MIN = 1;
  private static final int SCALE_MAX = 5;

  private ProfileLoader() {}

  /**
   * Couple file: {@code {"a": Profile, "b": Profile, "today": "yyyy-MM-dd"}}.
   *
   * @param a first person
   * @param b second person
   * @param today reference day of the couple
   */
  public record CoupleFile(Profile a, Profile b, LocalDate today) {}

  /**
   * Loads one profile.
   *
   * @param json JSON file with a single profile
   * @return the validated profile
   * @throws ContextException if the file cannot be read or parsed, a key is unknown or missing, a
   *     value is out of range or the window crosses midnight
   * @implNote O(n) time and space in the size of the file.
   */
  public static Profile load(Path json) throws ContextException {
    return toProfile(read(json, ProfileJson.class), "perfil");
  }

  /**
   * Loads a couple file.
   *
   * @param json JSON file with keys {@code a}, {@code b} and {@code today}
   * @return both profiles and the reference day
   * @throws ContextException if a profile is invalid or {@code today} is missing
   * @implNote O(n) time and space in the size of the file.
   */
  public static CoupleFile loadCouple(Path json) throws ContextException {
    CoupleJson couple = read(json, CoupleJson.class);
    Profile a = toProfile(couple.a(), "a");
    Profile b = toProfile(couple.b(), "b");
    LocalDate today = require(couple.today(), "today");
    return new CoupleFile(a, b, today);
  }

  private static <T> T read(Path json, Class<T> type) throws ContextException {
    T value;
    try {
      value = MAPPER.readValue(json.toFile(), type);
    } catch (IOException e) {
      throw new ContextException("no se pudo leer " + json + ": " + e.getMessage(), e);
    }
    return require(value, json.toString());
  }

  private static Profile toProfile(ProfileJson json, String path) throws ContextException {
    ProfileJson p = require(json, path);
    return new Profile(
        require(p.userId(), path + ".user_id"),
        require(p.borough(), path + ".borough"),
        location(p.location(), path + ".location"),
        preferences(p.preferences(), path + ".preferences"),
        emotions(p.emotionalRecent(), path + ".emotional_recent"),
        history(p.history(), path + ".history"),
        constraints(p.constraints(), path + ".constraints"),
        truthWeights(p.truthWeights(), path + ".truth_weights"));
  }

  private static Optional<Location> location(LocationJson json, String path)
      throws ContextException {
    if (json == null) {
      return Optional.empty();
    }
    return Optional.of(
        new Location(require(json.lat(), path + ".lat"), require(json.lon(), path + ".lon")));
  }

  private static Map<String, Integer> preferences(Map<String, Integer> json, String path)
      throws ContextException {
    Map<String, Integer> out = new LinkedHashMap<>();
    if (json != null) {
      for (Map.Entry<String, Integer> entry : json.entrySet()) {
        out.put(entry.getKey(), scale(entry.getValue(), path + "." + entry.getKey()));
      }
    }
    return out;
  }

  private static List<EmotionEntry> emotions(List<EmotionJson> json, String path)
      throws ContextException {
    List<EmotionEntry> out = new ArrayList<>();
    if (json != null) {
      for (int i = 0; i < json.size(); i++) {
        String at = path + "[" + i + "]";
        EmotionJson e = require(json.get(i), at);
        out.add(
            new EmotionEntry(
                require(e.date(), at + ".date"),
                require(e.code(), at + ".code"),
                scale(e.intensity(), at + ".intensity")));
      }
    }
    return out;
  }

  private static List<HistoryEntry> history(List<HistoryJson> json, String path)
      throws ContextException {
    List<HistoryEntry> out = new ArrayList<>();
    if (json != null) {
      for (int i = 0; i < json.size(); i++) {
        out.add(historyEntry(json.get(i), path + "[" + i + "]"));
      }
    }
    return out;
  }

  private static HistoryEntry historyEntry(HistoryJson json, String at) throws ContextException {
    HistoryJson h = require(json, at);
    OptionalInt rating =
        h.rating() == null
            ? OptionalInt.empty()
            : OptionalInt.of(scale(h.rating(), at + ".rating"));
    return new HistoryEntry(
        require(h.activityId(), at + ".activity_id"),
        require(h.date(), at + ".date"),
        require(h.offered(), at + ".offered"),
        require(h.chosen(), at + ".chosen"),
        rating);
  }

  private static Constraints constraints(ConstraintsJson json, String path)
      throws ContextException {
    ConstraintsJson c = require(json, path);
    LocalTime start = require(c.windowStart(), path + ".window_start");
    LocalTime end = require(c.windowEnd(), path + ".window_end");
    if (!end.isAfter(start)) {
      throw new ContextException(
          path + ": la ventana " + start + "-" + end + " cruza medianoche o está vacía");
    }
    return new Constraints(
        oneOf(c.budgetBand(), BUDGET_BANDS, path + ".budget_band"),
        oneOf(c.travelBand(), TRAVEL_BANDS, path + ".travel_band"),
        require(c.windowDay(), path + ".window_day"),
        start,
        end);
  }

  private static Optional<Map<Feature, Double>> truthWeights(Map<Feature, Double> json, String path)
      throws ContextException {
    if (json == null) {
      return Optional.empty();
    }
    for (Map.Entry<Feature, Double> entry : json.entrySet()) {
      require(entry.getValue(), path + "." + entry.getKey());
    }
    return Optional.of(json);
  }

  private static int scale(Integer value, String path) throws ContextException {
    int v = require(value, path);
    if (v < SCALE_MIN || v > SCALE_MAX) {
      throw new ContextException(path + ": " + v + " fuera de [1, 5]");
    }
    return v;
  }

  private static String oneOf(String value, Set<String> allowed, String path)
      throws ContextException {
    String v = require(value, path);
    if (!allowed.contains(v)) {
      throw new ContextException(path + ": '" + v + "' no es uno de " + allowed);
    }
    return v;
  }

  private static <T> T require(T value, String path) throws ContextException {
    if (value == null) {
      throw new ContextException(path + ": campo obligatorio ausente");
    }
    return value;
  }

  /** JSON shape of a couple file; nullable fields are validated by the loader. */
  record CoupleJson(ProfileJson a, ProfileJson b, LocalDate today) {}

  /** JSON shape of a profile. */
  record ProfileJson(
      Integer userId,
      String borough,
      LocationJson location,
      Map<String, Integer> preferences,
      List<EmotionJson> emotionalRecent,
      List<HistoryJson> history,
      ConstraintsJson constraints,
      Map<Feature, Double> truthWeights) {}

  /** JSON shape of a location. */
  record LocationJson(Double lat, Double lon) {}

  /** JSON shape of an emotional record. */
  record EmotionJson(LocalDate date, String code, Integer intensity) {}

  /** JSON shape of a history entry. */
  record HistoryJson(
      String activityId, LocalDate date, Boolean offered, Boolean chosen, Integer rating) {}

  /** JSON shape of the constraints. */
  record ConstraintsJson(
      String budgetBand,
      String travelBand,
      DayOfWeek windowDay,
      LocalTime windowStart,
      LocalTime windowEnd) {}
}
