package com.nexus.nexussync.sampler;

import static com.nexus.nexussync.sampler.EmotionAmbience.ACTIVE;
import static com.nexus.nexussync.sampler.EmotionAmbience.CULTURAL;
import static com.nexus.nexussync.sampler.EmotionAmbience.HOME;
import static com.nexus.nexussync.sampler.EmotionAmbience.NATURE;
import static com.nexus.nexussync.sampler.EmotionAmbience.QUIET;
import static com.nexus.nexussync.sampler.EmotionAmbience.ROMANTIC;
import static com.nexus.nexussync.sampler.EmotionAmbience.SOCIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.nexussync.context.Climate;
import com.nexus.nexussync.context.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Emotional grid valence x energy (T-23). */
final class EmotionAmbienceTest {

  // U4-17
  @Test
  void given_lowValenceLowEnergy_when_preferred_then_comfortingAmbience() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.LOW, Level.LOW)))
        .containsExactlyInAnyOrder(QUIET, NATURE, HOME);
  }

  // U4-17
  @Test
  void given_lowValenceMidEnergy_when_preferred_then_softActivation() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.LOW, Level.MID)))
        .containsExactlyInAnyOrder(NATURE, QUIET);
  }

  // U4-17
  @Test
  void given_lowValenceHighEnergy_when_preferred_then_neverRomantic() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.LOW, Level.HIGH)))
        .containsExactlyInAnyOrder(ACTIVE, NATURE)
        .doesNotContain(ROMANTIC);
  }

  // U4-17
  @Test
  void given_midValenceLowEnergy_when_preferred_then_comfortingRituals() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.MID, Level.LOW)))
        .containsExactlyInAnyOrder(QUIET, ROMANTIC, HOME);
  }

  // U4-17
  @Test
  void given_midValenceMidEnergy_when_preferred_then_everyAmbience() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.MID, Level.MID)))
        .isEqualTo(EmotionAmbience.ALL);
  }

  // U4-17
  @Test
  void given_midValenceHighEnergy_when_preferred_then_selfExpansion() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.MID, Level.HIGH)))
        .containsExactlyInAnyOrder(ACTIVE, CULTURAL);
  }

  // U4-17
  @Test
  void given_highValenceLowEnergy_when_preferred_then_quietSavouring() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.HIGH, Level.LOW)))
        .containsExactlyInAnyOrder(ROMANTIC, QUIET);
  }

  // U4-17
  @Test
  void given_highValenceMidEnergy_when_preferred_then_activeCapitalisation() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.HIGH, Level.MID)))
        .containsExactlyInAnyOrder(ACTIVE, CULTURAL, SOCIAL);
  }

  // U4-17
  @Test
  void given_highValenceHighEnergy_when_preferred_then_exploration() {
    assertThat(EmotionAmbience.preferred(new Climate(Level.HIGH, Level.HIGH)))
        .containsExactlyInAnyOrder(ACTIVE, CULTURAL, SOCIAL, NATURE);
  }

  // U4-17
  @ParameterizedTest
  @EnumSource(Level.class)
  void given_unknownOnEitherAxis_when_preferred_then_everyAmbience(Level other) {
    assertThat(EmotionAmbience.preferred(new Climate(Level.UNKNOWN, other)))
        .isEqualTo(EmotionAmbience.ALL);
    assertThat(EmotionAmbience.preferred(new Climate(other, Level.UNKNOWN)))
        .isEqualTo(EmotionAmbience.ALL);
  }

  @Test
  void given_anyCell_when_preferred_then_onlySchemaValues() {
    for (Level v : Level.values()) {
      for (Level e : Level.values()) {
        assertThat(EmotionAmbience.preferred(new Climate(v, e)))
            .isNotEmpty()
            .isSubsetOf(EmotionAmbience.ALL);
      }
    }
  }

  @Test
  void given_nullClimate_when_preferred_then_npe() {
    assertThatThrownBy(() -> EmotionAmbience.preferred(null))
        .isInstanceOf(NullPointerException.class);
  }
}
