package com.nexus.nexussync.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.params.Feature;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileLoaderTest {

  private static final String VALID =
      """
      {
        "user_id": 7,
        "borough": "Tlalpan",
        "preferences": { "senderismo": 5, "leer": 2 },
        "emotional_recent": [ { "date": "2026-09-25", "code": "CALM", "intensity": 3 } ],
        "history": [
          { "activity_id": "picnic", "date": "2026-09-20", "offered": true, "chosen": true }
        ],
        "constraints": {
          "budget_band": "LOW", "travel_band": "NEAR",
          "window_day": "SUNDAY", "window_start": "10:00", "window_end": "14:00"
        }
      }
      """;

  @TempDir Path tmp;

  private Path write(String json) throws IOException {
    Path file = tmp.resolve("profile.json");
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return file;
  }

  private static Path resource(String name) throws URISyntaxException {
    URL url = ProfileLoaderTest.class.getResource("/nexussync/couples/" + name);
    assertThat(url).as(name).isNotNull();
    return Path.of(url.toURI());
  }

  // U3-01
  @Test
  void given_couple_file_when_load_couple_then_both_profiles_and_today_are_parsed()
      throws Exception {
    ProfileLoader.CoupleFile couple = ProfileLoader.loadCouple(resource("opuestos.json"));

    assertThat(couple.today()).isEqualTo(LocalDate.of(2026, 9, 27));
    Profile a = couple.a();
    assertThat(a.userId()).isEqualTo(101);
    assertThat(a.location()).contains(new Location(19.3467, -99.1617));
    assertThat(a.preferences()).containsEntry("gimnasio", 5).containsEntry("leer", 1);
    assertThat(a.emotionalRecent()).hasSize(2);
    assertThat(a.history().get(0).rating()).isEqualTo(OptionalInt.of(3));
    assertThat(a.history().get(1).rating()).isEmpty();
    assertThat(a.constraints())
        .isEqualTo(
            new Constraints(
                "MID", "MID", DayOfWeek.SATURDAY, LocalTime.of(16, 0), LocalTime.of(22, 0)));
    assertThat(a.truthWeights()).hasValueSatisfying(w -> assertThat(w).containsKey(Feature.PRICE));
    assertThat(couple.b().userId()).isEqualTo(102);
  }

  // U3-01
  @Test
  void given_profile_without_optional_keys_when_load_then_optionals_are_empty() throws Exception {
    Profile p = ProfileLoader.load(write(VALID));

    assertThat(p.location()).isEmpty();
    assertThat(p.truthWeights()).isEmpty();
    assertThat(p.history().get(0).rating()).isEmpty();
    assertThat(p.emotionalRecent().get(0).code()).isEqualTo("CALM");
  }

  // U3-01
  @Test
  void given_profile_without_lists_when_load_then_lists_are_empty() throws Exception {
    String json =
        """
        { "user_id": 1, "borough": "X", "constraints": { "budget_band": "FREE",
          "travel_band": "FAR", "window_day": "MONDAY", "window_start": "08:00",
          "window_end": "09:00" } }
        """;

    Profile p = ProfileLoader.load(write(json));

    assertThat(p.preferences()).isEmpty();
    assertThat(p.emotionalRecent()).isEmpty();
    assertThat(p.history()).isEmpty();
  }

  // U3-02
  @Test
  void given_window_crossing_midnight_when_load_couple_then_context_exception() {
    assertThatThrownBy(() -> ProfileLoader.loadCouple(resource("midnight-window.json")))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("a.constraints")
        .hasMessageContaining("medianoche");
  }

  // U3-02
  @Test
  void given_couple_without_today_when_load_couple_then_context_exception() {
    assertThatThrownBy(() -> ProfileLoader.loadCouple(resource("no-today.json")))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("today");
  }

  // U3-02
  @Test
  void given_empty_window_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"14:00\"", "\"10:00\""));

    assertThatThrownBy(() -> ProfileLoader.load(file)).isInstanceOf(ContextException.class);
  }

  @Test
  void given_unknown_budget_band_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"LOW\"", "\"CHEAP\""));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("budget_band");
  }

  @Test
  void given_unknown_travel_band_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"NEAR\"", "\"MOON\""));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("travel_band");
  }

  @Test
  void given_preference_level_out_of_range_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"leer\": 2", "\"leer\": 6"));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("preferences.leer");
  }

  @Test
  void given_intensity_below_range_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"intensity\": 3", "\"intensity\": 0"));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("intensity");
  }

  @Test
  void given_rating_out_of_range_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"chosen\": true", "\"chosen\": true, \"rating\": 9"));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("rating");
  }

  @Test
  void given_valid_rating_when_load_then_rating_present() throws Exception {
    Path file = write(VALID.replace("\"chosen\": true", "\"chosen\": true, \"rating\": 4"));

    assertThat(ProfileLoader.load(file).history().get(0).rating()).isEqualTo(OptionalInt.of(4));
  }

  @Test
  void given_missing_user_id_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"user_id\": 7,", ""));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("user_id");
  }

  @Test
  void given_unknown_key_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"user_id\": 7,", "\"user_id\": 7, \"shoe_size\": 42,"));

    assertThatThrownBy(() -> ProfileLoader.load(file)).isInstanceOf(ContextException.class);
  }

  @Test
  void given_json_null_when_load_then_context_exception() throws Exception {
    Path file = write("null");

    assertThatThrownBy(() -> ProfileLoader.load(file)).isInstanceOf(ContextException.class);
  }

  @Test
  void given_missing_file_when_load_then_context_exception() {
    Path file = tmp.resolve("missing.json");

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void given_location_without_lon_when_load_then_context_exception() throws Exception {
    Path file =
        write(VALID.replace("\"user_id\": 7,", "\"user_id\": 7, \"location\": {\"lat\": 1},"));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("location.lon");
  }

  @Test
  void given_null_truth_weight_when_load_then_context_exception() throws Exception {
    Path file =
        write(
            VALID.replace(
                "\"user_id\": 7,", "\"user_id\": 7, \"truth_weights\": {\"PRICE\": null},"));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("truth_weights.PRICE");
  }

  @Test
  void given_null_emotion_entry_when_load_then_context_exception() throws Exception {
    Path file = write(VALID.replace("\"emotional_recent\": [", "\"emotional_recent\": [ null,"));

    assertThatThrownBy(() -> ProfileLoader.load(file))
        .isInstanceOf(ContextException.class)
        .hasMessageContaining("emotional_recent[0]");
  }
}
