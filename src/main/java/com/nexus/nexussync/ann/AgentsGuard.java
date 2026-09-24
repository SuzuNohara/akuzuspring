package com.nexus.nexussync.ann;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Guards that the arkannie home only exposes the nexussync agents (V9, U6-13).
 *
 * <p>{@code nexussyncDir/.agents} must contain exactly the subdirectories {@code persona} and
 * {@code mediador}, each with an {@code agent.yaml} or {@code agent.yaml.tmpl} and a {@code
 * harness.md}. Anything else means arkannie could dispatch an agent outside the design.
 */
public final class AgentsGuard {

  /** The only agents allowed in the nexussync home. */
  static final Set<String> EXPECTED = Set.of("persona", "mediador");

  private static final String AGENTS_DIR = ".agents";
  private static final String AGENT_YAML = "agent.yaml";
  private static final String AGENT_TEMPLATE = "agent.yaml.tmpl";
  private static final String HARNESS = "harness.md";

  private AgentsGuard() {}

  /**
   * Fails unless {@code nexussyncDir/.agents} holds exactly the expected, complete agents.
   *
   * @param nexussyncDir root of the nexussync home, never {@code null}
   * @throws AnnException {@code AGENTS} naming the extra, missing or incomplete agent, or if the
   *     directory cannot be listed
   * @implNote O(a log a) time and O(a) space, a = number of entries in {@code .agents}.
   */
  public static void check(Path nexussyncDir) throws AnnException {
    Set<String> found = listAgents(nexussyncDir.resolve(AGENTS_DIR));
    for (String agent : found) {
      if (!EXPECTED.contains(agent)) {
        throw new AnnException(AnnException.Kind.AGENTS, "unexpected agent in .agents: " + agent);
      }
    }
    for (String agent : new TreeSet<>(EXPECTED)) {
      if (!found.contains(agent)) {
        throw new AnnException(AnnException.Kind.AGENTS, "missing agent in .agents: " + agent);
      }
      requireFiles(nexussyncDir.resolve(AGENTS_DIR).resolve(agent), agent);
    }
  }

  private static Set<String> listAgents(Path agentsDir) throws AnnException {
    Set<String> names = new TreeSet<>();
    if (!Files.isDirectory(agentsDir)) {
      return names;
    }
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(agentsDir, Files::isDirectory)) {
      for (Path entry : entries) {
        names.add(String.valueOf(entry.getFileName()));
      }
    } catch (IOException e) {
      throw new AnnException(AnnException.Kind.AGENTS, "cannot list " + agentsDir, e);
    }
    return names;
  }

  private static void requireFiles(Path agentDir, String agent) throws AnnException {
    boolean hasYaml =
        Files.isRegularFile(agentDir.resolve(AGENT_YAML))
            || Files.isRegularFile(agentDir.resolve(AGENT_TEMPLATE));
    if (!hasYaml) {
      throw new AnnException(
          AnnException.Kind.AGENTS,
          "agent " + agent + " has neither " + AGENT_YAML + " nor " + AGENT_TEMPLATE);
    }
    if (!Files.isRegularFile(agentDir.resolve(HARNESS))) {
      throw new AnnException(AnnException.Kind.AGENTS, "agent " + agent + " has no " + HARNESS);
    }
  }
}
