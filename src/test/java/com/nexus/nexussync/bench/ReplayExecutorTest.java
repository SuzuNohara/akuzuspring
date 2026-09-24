package com.nexus.nexussync.bench;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.nexussync.ann.CallBudget;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.catalog.Catalog;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.context.ContextBuilder;
import com.nexus.nexussync.context.ProfileLoader;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.FakeGateExecutor;
import com.nexus.nexussync.rounds.Gate;
import com.nexus.nexussync.rounds.GateResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayExecutorTest {

  @TempDir Path home;
  @TempDir Path out;
  @TempDir Path replayRun;

  // U11-06
  @Test
  void given_recordedRunWithRetry_when_replayedThroughGate_then_sameGateResult() throws Exception {
    Path nx = BenchFixtures.nexussyncDir();
    Params p = BenchFixtures.params(nx, home);
    Catalog cat = BenchFixtures.catalog(nx, p);
    Path couple = BenchFixtures.couple(nx, "opuestos");
    FakeGateExecutor live = new FakeGateExecutor().echoSample().failTimes(Agent.A, 1);
    RunRecord recorded =
        new CoupleRunner(BenchFixtures.ticking())
            .run(couple, p, cat, live, out, 1, new Random(p.seed()), new CallBudget(200))
            .get(0);
    Path recordedDir =
        out.resolve(recorded.experiment()).resolve(recorded.coupleId()).resolve(recorded.runId());
    assertThat(recordedDir.resolve("round1-2.out.md")).isRegularFile();
    ProfileLoader.CoupleFile cf = ProfileLoader.loadCouple(couple);
    Context ctx = ContextBuilder.build(cf.a(), cf.b(), cf.today(), p, Map.of());

    GateResult replayed =
        new Gate()
            .run(
                recorded.sample(),
                ctx,
                cat,
                p,
                new ReplayExecutor(recordedDir),
                replayRun,
                new CallBudget(200));

    GateResult original =
        BenchIo.JSON.readValue(recordedDir.resolve(CoupleRunner.GATE).toFile(), GateResult.class);
    assertThat(replayed).isEqualTo(original).isEqualTo(recorded.gate());
  }

  // U11-06
  @Test
  void given_noRound2Output_when_round2_then_emptyPerAgent(@TempDir Path empty) throws Exception {
    ReplayExecutor ex = new ReplayExecutor(empty);

    Map<Agent, Optional<Envelope>> votes = ex.round2(replayRun, List.of("x"), null);
    Map<Agent, Optional<Envelope>> picks =
        ex.round1(replayRun, null, null, null, EnumSet.allOf(Agent.class));

    assertThat(votes).containsOnlyKeys(Agent.A, Agent.B);
    assertThat(votes.values()).allSatisfy(v -> assertThat(v).isEmpty());
    assertThat(picks).containsOnlyKeys(Agent.A, Agent.B, Agent.M);
    assertThat(picks.values()).allSatisfy(v -> assertThat(v).isEmpty());
  }

  // U11-06
  @Test
  void given_recordedRound2_when_round2Twice_then_firstAttemptThenSecondMissing(@TempDir Path dir)
      throws Exception {
    Files.writeString(
        dir.resolve("round2.out.md"),
        "---\nstatus: success\n---\n\n## r-1\n\n```yaml\nid: a\nstatus: success\npayload:\n"
            + "  votes: [x]\n```\n",
        StandardCharsets.UTF_8);
    ReplayExecutor ex = new ReplayExecutor(dir);

    Map<Agent, Optional<Envelope>> first = ex.round2(replayRun, List.of("x"), null);
    Map<Agent, Optional<Envelope>> second = ex.round2(replayRun, List.of("x"), null);

    assertThat(first.get(Agent.A)).get().extracting(Envelope::id).isEqualTo("a");
    assertThat(first.get(Agent.B)).isEmpty();
    assertThat(second.values()).allSatisfy(v -> assertThat(v).isEmpty());
  }

  // U11-06
  @Test
  void given_corruptOutput_when_round1_then_degradedToEmpty(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("round1.out.md"), "sin front matter", StandardCharsets.UTF_8);

    Map<Agent, Optional<Envelope>> picks =
        new ReplayExecutor(dir).round1(replayRun, null, null, null, EnumSet.of(Agent.M));

    assertThat(picks).containsOnlyKeys(Agent.M);
    assertThat(picks.get(Agent.M)).isEmpty();
  }
}
