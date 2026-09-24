package com.nexus.nexussync.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.params.MediatorClimate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouplesFixtureTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

  private static Path couplesDir() {
    String dir = System.getProperty("nexussync.dir", "");
    Path couples = Path.of(dir.isEmpty() ? "missing-nexussync-dir" : dir, "fixtures", "couples");
    Assumptions.assumeTrue(Files.isDirectory(couples), "sin nexussync.dir/fixtures/couples");
    return couples;
  }

  private static ProfileLoader.CoupleFile load(String slug) throws ContextException {
    return ProfileLoader.loadCouple(couplesDir().resolve(slug + ".json"));
  }

  private static Context context(String slug) throws ContextException {
    ProfileLoader.CoupleFile couple = load(slug);
    return ContextBuilder.build(
        couple.a(),
        couple.b(),
        couple.today(),
        ContextBuilderTest.params(MediatorClimate.AGGREGATED),
        Map.of());
  }

  // U3-01
  @ParameterizedTest
  @ValueSource(
      strings = {
        "opuestos",
        "frio-total",
        "bajo-presupuesto",
        "clima-bajo",
        "clima-alto",
        "sin-ubicacion",
        "historial-largo",
        "hogar",
        "deportistas",
        "real-4-8"
      })
  void given_couple_fixture_when_load_then_valid_with_window_and_fixed_today(String slug)
      throws Exception {
    ProfileLoader.CoupleFile couple = load(slug);

    assertThat(couple.today()).isEqualTo(TODAY);
    assertThat(couple.a().preferences()).isNotEmpty();
    assertThat(couple.b().preferences()).isNotEmpty();
    assertThat(Windows.overlap(couple.a().constraints(), couple.b().constraints())).isNotEmpty();
    boolean synthetic = !"real-4-8".equals(slug);
    assertThat(couple.a().truthWeights().isPresent()).isEqualTo(synthetic);
    assertThat(couple.b().truthWeights().isPresent()).isEqualTo(synthetic);
  }

  // U3-01
  @Test
  void given_real_couple_when_load_then_users_4_and_8_with_preferences_only() throws Exception {
    ProfileLoader.CoupleFile couple = load("real-4-8");

    assertThat(ContextBuilder.coupleId(couple.a().userId(), couple.b().userId())).isEqualTo("4-8");
    assertThat(couple.a().history()).isEmpty();
    assertThat(couple.a().emotionalRecent()).isEmpty();
    assertThat(couple.a().location()).isEmpty();
  }

  // U3-01
  @Test
  void given_cold_couple_when_build_then_no_history_and_unknown_climate() throws Exception {
    Context ctx = context("frio-total");

    assertThat(ctx.a().history()).isEmpty();
    assertThat(ctx.b().emotionalRecent()).isEmpty();
    assertThat(ctx.climateA()).isEqualTo(new Climate(Level.UNKNOWN, Level.UNKNOWN));
    assertThat(ctx.ratedDatesCount()).isZero();
  }

  // U3-01
  @Test
  void given_low_budget_couple_when_load_then_free_and_low_bands() throws Exception {
    ProfileLoader.CoupleFile couple = load("bajo-presupuesto");

    assertThat(couple.a().constraints().budgetBand()).isEqualTo("FREE");
    assertThat(couple.b().constraints().budgetBand()).isEqualTo("LOW");
  }

  // U3-01
  @Test
  void given_climate_couples_when_build_then_low_and_high_valence() throws Exception {
    Context low = context("clima-bajo");
    Context high = context("clima-alto");
    Context sporty = context("deportistas");

    assertThat(low.climateA().valence()).isEqualTo(Level.LOW);
    assertThat(low.climateB().valence()).isEqualTo(Level.LOW);
    assertThat(high.climateA().valence()).isEqualTo(Level.HIGH);
    assertThat(high.climateB().valence()).isEqualTo(Level.HIGH);
    assertThat(sporty.climateA().energy()).isEqualTo(Level.HIGH);
    assertThat(sporty.climateB().energy()).isEqualTo(Level.HIGH);
  }

  // U3-01
  @Test
  void given_couple_without_location_when_load_then_both_empty() throws Exception {
    ProfileLoader.CoupleFile couple = load("sin-ubicacion");

    assertThat(couple.a().location()).isEmpty();
    assertThat(couple.b().location()).isEmpty();
  }

  // U3-01
  @Test
  void given_long_history_couple_when_build_then_at_least_twenty_rated_dates() throws Exception {
    Context ctx = context("historial-largo");

    assertThat(ctx.a().history()).hasSizeGreaterThanOrEqualTo(20);
    assertThat(ctx.ratedDatesCount()).isGreaterThanOrEqualTo(20);
  }

  // U3-01
  @Test
  void given_home_couple_when_load_then_near_low_and_home_interests() throws Exception {
    ProfileLoader.CoupleFile couple = load("hogar");

    assertThat(couple.a().constraints().travelBand()).isEqualTo("NEAR");
    assertThat(couple.a().constraints().budgetBand()).isEqualTo("LOW");
    assertThat(couple.a().preferences()).containsEntry("quedarse-en-casa", 5);
    assertThat(couple.b().preferences()).containsEntry("quedarse-en-casa", 5);
  }
}
