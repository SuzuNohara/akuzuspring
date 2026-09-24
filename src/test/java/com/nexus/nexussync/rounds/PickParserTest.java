package com.nexus.nexussync.rounds;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.ann.Envelope;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PickParserTest {

  private static final Set<String> SAMPLE = Set.of("s1", "s2", "s3", "s4", "s5");

  private static Optional<Envelope> env(String status, Map<String, Object> payload) {
    return Optional.of(new Envelope("a", status, payload));
  }

  // U7-01
  @Test
  void given_validPicksWithReasons_when_parse_then_okWithAlignedReasons() {
    Pick pick =
        PickParser.parse(
            Agent.A,
            env(
                "success",
                Map.of("picks", List.of("s1", "s2", "s3"), "reasons", List.of("r1", "r2", "r3"))),
            SAMPLE,
            3);

    assertThat(pick.status()).isEqualTo(PickStatus.OK);
    assertThat(pick.ok()).isTrue();
    assertThat(pick.agent()).isEqualTo(Agent.A);
    assertThat(pick.ids()).containsExactly("s1", "s2", "s3");
    assertThat(pick.reasons()).containsExactly("r1", "r2", "r3");
    assertThat(pick.hallucinated()).isEmpty();
  }

  // U7-01
  @Test
  void given_votesKeyAndNonStringIds_when_parse_then_readsVotesAsStrings() {
    Pick pick =
        PickParser.parse(
            Agent.B, env("success", Map.of("votes", List.of(7, "s2"))), Set.of("7", "s2"), 5);

    assertThat(pick.ids()).containsExactly("7", "s2");
    assertThat(pick.reasons()).containsExactly("", "");
  }

  // U7-02
  @Test
  void given_hallucinationsDuplicatesAndExtraIds_when_parse_then_filtersDedupsAndCuts() {
    Pick pick =
        PickParser.parse(
            Agent.A,
            env(
                "success",
                Map.of(
                    "picks",
                    List.of("s1", "x9", "s1", "s2", "x9", "s3", "s4"),
                    "reasons",
                    List.of("r1", "rx", "rdup", "r2"))),
            SAMPLE,
            3);

    assertThat(pick.status()).isEqualTo(PickStatus.OK);
    assertThat(pick.ids()).containsExactly("s1", "s2", "s3");
    assertThat(pick.reasons()).containsExactly("r1", "r2", "");
    assertThat(pick.hallucinated()).containsExactly("x9");
  }

  // U7-02
  @Test
  void given_mediatorReturnsThreeWithFiveAllowed_when_parse_then_okWithThree() {
    Pick pick =
        PickParser.parse(
            Agent.M, env("success", Map.of("picks", List.of("s1", "s2", "s3"))), SAMPLE, 5);

    assertThat(pick.status()).isEqualTo(PickStatus.OK);
    assertThat(pick.ids()).containsExactly("s1", "s2", "s3");
  }

  // U7-02
  @Test
  void given_onlyHallucinatedIds_when_parse_then_failedKeepingHallucinations() {
    Pick pick =
        PickParser.parse(Agent.A, env("success", Map.of("picks", List.of("x1", "x2"))), SAMPLE, 5);

    assertThat(pick.status()).isEqualTo(PickStatus.FAILED);
    assertThat(pick.ids()).isEmpty();
    assertThat(pick.hallucinated()).containsExactly("x1", "x2");
  }

  // U7-03
  @Test
  void given_missingEnvelope_when_parse_then_failed() {
    Pick pick = PickParser.parse(Agent.B, Optional.empty(), SAMPLE, 5);

    assertThat(pick.status()).isEqualTo(PickStatus.FAILED);
    assertThat(pick.ok()).isFalse();
    assertThat(pick.agent()).isEqualTo(Agent.B);
  }

  // U7-03
  @Test
  void given_nonSuccessStatus_when_parse_then_failed() {
    Map<String, Object> payload = Map.of("picks", List.of("s1"));

    assertThat(PickParser.parse(Agent.A, env("ok", payload), SAMPLE, 5).status())
        .isEqualTo(PickStatus.FAILED);
    assertThat(PickParser.parse(Agent.A, env("error", payload), SAMPLE, 5).status())
        .isEqualTo(PickStatus.FAILED);
  }

  // U7-03
  @Test
  void given_payloadWithoutListOfIds_when_parse_then_failed() {
    Map<String, Object> nullPicks = new HashMap<>();
    nullPicks.put("picks", null);

    assertThat(PickParser.parse(Agent.A, env("success", Map.of()), SAMPLE, 5).status())
        .isEqualTo(PickStatus.FAILED);
    assertThat(PickParser.parse(Agent.A, env("success", Map.of("picks", "s1")), SAMPLE, 5).status())
        .isEqualTo(PickStatus.FAILED);
    assertThat(PickParser.parse(Agent.A, env("success", nullPicks), SAMPLE, 5).status())
        .isEqualTo(PickStatus.FAILED);
  }

  // U7-03
  @Test
  void given_reasonsNotList_when_parse_then_emptyReasons() {
    Pick pick =
        PickParser.parse(
            Agent.A,
            env("success", Map.of("picks", Arrays.asList("s1"), "reasons", "texto")),
            SAMPLE,
            5);

    assertThat(pick.reasons()).containsExactly("");
  }
}
