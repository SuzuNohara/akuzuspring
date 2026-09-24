package com.nexus.nexussync.rounds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.ArkannieRunner;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.ann.ProgramRenderer;
import com.nexus.nexussync.ann.RunOutput;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.AgentsParams;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.params.RuntimeParams;
import com.nexus.nexussync.sampler.Sample;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real {@link GateExecutor}: renders the Ann program of each round from {@code nexussyncDir/ann/},
 * runs it with arkannie and returns the envelope of each agent (unit U7, §3.7).
 *
 * <p>Round one writes the five traces of the run ({@link Gate#writeTraces}) and renders {@code
 * round1.ann} with their absolute paths; round two writes {@code shortlist.json} and renders {@code
 * round2.ann}. The arkannie run id is the name of {@code runDir} plus {@code -r1}/{@code -r2}, and
 * {@code .output/<runId>.md} is copied next to the program as {@code round1.out.md}/{@code
 * round2.out.md} so the run can be replayed. A second attempt of the same round in the same {@code
 * runDir} (the gate's retry) adds {@code -2}, {@code -3}… to the run id, the program and the copied
 * output, so no attempt overwrites another.
 *
 * <p>An {@link AnnException} of the runner degrades the round: every agent of that round gets
 * {@code Optional.empty()} and the gate applies its degradation rules. {@code LOCKED} and {@code
 * AGENTS} are not degradations but misconfigurations of the home, so they propagate, as do errors
 * rendering the program or writing the traces.
 */
public final class ArkannieExecutor implements GateExecutor {

  /** Directory of the Ann templates under {@code nexussyncDir}. */
  static final String ANN_DIR = "ann";

  /** Shortlist handed to the personas in round two. */
  static final String SHORTLIST = "shortlist.json";

  /** Extra wall-clock time granted to the arkannie process over the slowest agent timeout. */
  static final Duration GRACE = Duration.ofSeconds(30);

  private static final Logger LOG = LoggerFactory.getLogger(ArkannieExecutor.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<Agent> ROUND1 = List.of(Agent.A, Agent.B, Agent.M);
  private static final List<Agent> ROUND2 = List.of(Agent.A, Agent.B);
  private static final String OUTPUT_DIR = ".output";
  private static final String MD = ".md";
  private static final String ANN = ".ann";

  private final ArkannieRunner runner;
  private final Path nexussyncDir;

  /**
   * Creates an executor over the arkannie home {@code nexussyncDir}.
   *
   * @param runner runner that launches arkannie, never {@code null}
   * @param nexussyncDir root of {@code ann/}, {@code .agents/} and {@code .output/}; it is also the
   *     {@code ARKANNIE_HOME} of every run, whatever {@code RuntimeParams.nexussyncDir} says
   * @implNote O(1) time and space.
   */
  public ArkannieExecutor(ArkannieRunner runner, Path nexussyncDir) {
    this.runner = Objects.requireNonNull(runner, "runner");
    this.nexussyncDir = Objects.requireNonNull(nexussyncDir, "nexussyncDir").toAbsolutePath();
  }

  /**
   * Writes the traces, renders and runs {@code round1.ann}: both personas pick and the mediator
   * recommends, {@code k = rounds.pickCount}.
   *
   * @implNote O(n) time and space in the sample and output sizes, plus one arkannie run.
   */
  @Override
  public Map<Agent, Optional<Envelope>> round1(Path runDir, Context ctx, Sample s, Params p)
      throws AnnException {
    Gate.writeTraces(runDir, ctx, s, p);
    Path dir = runDir.toAbsolutePath();
    Map<String, String> values = profiles(dir);
    values.put("sample", dir.resolve(Gate.SAMPLE).toString());
    values.put("view", dir.resolve(Gate.VIEW).toString());
    values.put("rubric", dir.resolve(Gate.RUBRIC).toString());
    values.put("k_pick", Integer.toString(p.rounds().pickCount()));
    return round(dir, 1, values, p, ROUND1);
  }

  /**
   * Writes {@code shortlist.json}, renders and runs {@code round2.ann}: both personas vote over the
   * shortlist, {@code k = min(rounds.voteCount, |shortlist|)}. The profiles are the ones written by
   * round one in the same {@code runDir}.
   *
   * @implNote O(n) time and space in the shortlist and output sizes, plus one arkannie run.
   */
  @Override
  public Map<Agent, Optional<Envelope>> round2(Path runDir, List<String> shortlist, Params p)
      throws AnnException {
    Path dir = runDir.toAbsolutePath();
    writeShortlist(dir, shortlist);
    Map<String, String> values = profiles(dir);
    values.put("shortlist", dir.resolve(SHORTLIST).toString());
    values.put("k_vote", Integer.toString(Math.min(p.rounds().voteCount(), shortlist.size())));
    return round(dir, 2, values, p, ROUND2);
  }

  private Map<Agent, Optional<Envelope>> round(
      Path dir, int round, Map<String, String> values, Params p, List<Agent> agents)
      throws AnnException {
    String suffix = attemptSuffix(dir, round);
    String name = "round" + round;
    Path template = nexussyncDir.resolve(ANN_DIR).resolve(name + ".ann.tmpl");
    Path program = ProgramRenderer.render(template, values, dir.resolve(name + suffix + ANN));
    String runId = dir.getFileName() + "-r" + round + suffix;
    Optional<RunOutput> out = launch(program, runId, p);
    copyOutput(runId, dir.resolve(name + suffix + ".out" + MD));
    Map<Agent, Optional<Envelope>> result = new EnumMap<>(Agent.class);
    for (Agent agent : agents) {
      String id = agent.name().toLowerCase(Locale.ROOT);
      result.put(agent, out.map(o -> o.envelopes().get(id)));
    }
    return result;
  }

  private Optional<RunOutput> launch(Path program, String runId, Params p) throws AnnException {
    try {
      return Optional.of(runner.run(program, runId, home(p.runtime()), timeout(p.agents())));
    } catch (AnnException e) {
      if (!degrades(e)) {
        throw e;
      }
      LOG.warn("arkannie run {} failed ({}), round degraded: {}", runId, e.kind(), e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Whether a runner error degrades the round ({@code true}) or must propagate ({@code false}):
   * {@code LOCKED} and {@code AGENTS} are misconfigurations of the home, not agent failures.
   *
   * @param e error thrown by the runner
   * @return {@code false} only for {@code LOCKED} and {@code AGENTS}
   * @implNote O(1) time and space.
   */
  static boolean degrades(AnnException e) {
    return e.kind() != AnnException.Kind.LOCKED && e.kind() != AnnException.Kind.AGENTS;
  }

  /** Same runtime, with this executor's home, so program, output and templates share one root. */
  private RuntimeParams home(RuntimeParams rt) {
    return new RuntimeParams(
        rt.executor(),
        nexussyncDir,
        rt.arkannieBin(),
        rt.replayDir(),
        rt.maxCalls(),
        rt.arkannieVersion());
  }

  private static Duration timeout(AgentsParams a) {
    return Duration.ofSeconds(Math.max(a.personaTimeout(), a.mediatorTimeout())).plus(GRACE);
  }

  /**
   * {@code ""} for the first attempt of a round in {@code dir}, then {@code -2}, {@code -3}…; an
   * attempt exists once its program has been rendered, whether or not arkannie produced output.
   */
  private static String attemptSuffix(Path dir, int round) {
    String base = "round" + round;
    if (!Files.exists(dir.resolve(base + ANN))) {
      return "";
    }
    int attempt = 2;
    while (Files.exists(dir.resolve(base + "-" + attempt + ANN))) {
      attempt++;
    }
    return "-" + attempt;
  }

  /** Copies {@code .output/<runId>.md} when arkannie wrote it, even for a failed run. */
  private void copyOutput(String runId, Path target) throws AnnException {
    Path source = nexussyncDir.resolve(OUTPUT_DIR).resolve(runId + MD);
    if (!Files.isRegularFile(source)) {
      return;
    }
    try {
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new AnnException(
          AnnException.Kind.RENDER, "cannot copy " + source + " to " + target, e);
    }
  }

  private static Map<String, String> profiles(Path dir) {
    Map<String, String> values = new HashMap<>();
    values.put("profile_a", dir.resolve(Gate.PROFILE_A).toString());
    values.put("profile_b", dir.resolve(Gate.PROFILE_B).toString());
    return values;
  }

  private static void writeShortlist(Path dir, List<String> shortlist) throws AnnException {
    Path out = dir.resolve(SHORTLIST);
    try {
      Files.createDirectories(dir);
      JSON.writeValue(out.toFile(), Map.of("shortlist", shortlist));
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.RENDER, "cannot write " + out, e);
    }
  }
}
