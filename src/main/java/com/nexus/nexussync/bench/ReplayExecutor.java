package com.nexus.nexussync.bench;

import com.nexus.nexussync.ann.AnnException;
import com.nexus.nexussync.ann.Envelope;
import com.nexus.nexussync.ann.OutputReader;
import com.nexus.nexussync.ann.RunOutput;
import com.nexus.nexussync.context.Context;
import com.nexus.nexussync.params.Params;
import com.nexus.nexussync.rounds.Agent;
import com.nexus.nexussync.rounds.GateExecutor;
import com.nexus.nexussync.sampler.Sample;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link GateExecutor} that repeats a recorded run without calling any agent (unit U11, §3.11).
 *
 * <p>The n-th call of a round reads the output that {@code ArkannieExecutor} copied for the n-th
 * attempt: {@code round<N>.out.md}, then {@code round<N>-2.out.md}, {@code round<N>-3.out.md}… A
 * missing file gives {@code Optional.empty()} for every requested agent, as does a requested agent
 * without envelope in the file. A file that cannot be parsed degrades the round the same way
 * (logged), as the live executor does. The parsing is the one of {@link OutputReader}: since it
 * only reads {@code <home>/.output/<runId>.md}, the recorded file is copied into a temporary home
 * that is deleted afterwards (declared adapter of T-62). Instances keep the attempt counters, so
 * use one per replayed run.
 */
public final class ReplayExecutor implements GateExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(ReplayExecutor.class);
  private static final String REPLAY_ID = "replay";
  private static final String OUTPUT_DIR = ".output";

  private final Path replayDir;
  private final Map<Integer, Integer> attempts = new HashMap<>();

  /**
   * Creates an executor over a recorded run directory.
   *
   * @param replayDir run directory holding {@code round*.out.md}, never {@code null}
   * @implNote O(1) time and space.
   */
  public ReplayExecutor(Path replayDir) {
    this.replayDir = Objects.requireNonNull(replayDir, "replayDir");
  }

  /**
   * Replays the next round-one attempt for the requested agents.
   *
   * @implNote O(n) time and space in the size of the recorded output.
   */
  @Override
  public Map<Agent, Optional<Envelope>> round1(
      Path runDir, Context ctx, Sample s, Params p, Set<Agent> agents) throws AnnException {
    return replay(1, agents);
  }

  /**
   * Replays the next round-two attempt for both personas.
   *
   * @implNote O(n) time and space in the size of the recorded output.
   */
  @Override
  public Map<Agent, Optional<Envelope>> round2(Path runDir, List<String> shortlist, Params p)
      throws AnnException {
    return replay(2, EnumSet.of(Agent.A, Agent.B));
  }

  /**
   * A replay calls no agent, so it never consumes the call budget (D-26).
   *
   * @return {@code false}
   * @implNote O(1) time and space.
   */
  @Override
  public boolean billable() {
    return false;
  }

  private Map<Agent, Optional<Envelope>> replay(int round, Set<Agent> agents) throws AnnException {
    int attempt = attempts.merge(round, 1, Integer::sum);
    String suffix = attempt == 1 ? "" : "-" + attempt;
    Path file = replayDir.resolve("round" + round + suffix + ".out.md");
    Map<String, Envelope> envelopes = Files.isRegularFile(file) ? read(file) : Map.of();
    Map<Agent, Optional<Envelope>> out = new EnumMap<>(Agent.class);
    for (Agent agent : agents) {
      String id = agent.name().toLowerCase(Locale.ROOT);
      out.put(agent, Optional.ofNullable(envelopes.get(id)));
    }
    return out;
  }

  /**
   * Parses a recorded output with {@link OutputReader} through a temporary home.
   *
   * @param file recorded {@code round*.out.md}
   * @return the envelopes by dispatch id; empty if the file cannot be parsed
   * @throws AnnException {@code NOT_FOUND} if the temporary home cannot be created
   * @implNote O(n) time and space in the size of the file.
   */
  static Map<String, Envelope> read(Path file) throws AnnException {
    Path home;
    try {
      home = Files.createTempDirectory("nexussync-replay");
      Path output = Files.createDirectories(home.resolve(OUTPUT_DIR));
      Files.copy(file, output.resolve(REPLAY_ID + ".md"));
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.NOT_FOUND, "cannot stage " + file, e);
    }
    try {
      RunOutput out = OutputReader.read(home, REPLAY_ID);
      return out.envelopes();
    } catch (AnnException e) {
      LOG.warn("recorded output {} unreadable ({}), round degraded", file, e.kind());
      return Map.of();
    } finally {
      delete(home);
    }
  }

  /** Deletes the temporary home; a failure only leaves a stray temporary directory (logged). */
  private static void delete(Path home) {
    try (Stream<Path> walk = Files.walk(home)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    } catch (IOException e) {
      LOG.warn("cannot delete temporary replay home {}", home, e);
    }
  }
}
