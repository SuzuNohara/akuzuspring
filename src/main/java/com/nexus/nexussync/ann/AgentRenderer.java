package com.nexus.nexussync.ann;

import com.nexus.nexussync.params.AgentsParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Renders {@code .agents/{persona,mediador}/agent.yaml} from their {@code agent.yaml.tmpl} (unit
 * U5).
 *
 * <p>Each template receives two keys through {@link ProgramRenderer}: {@code model} and {@code
 * timeout}, taken from the persona or mediator fields of {@link AgentsParams}. Everything is
 * validated before anything is written: both templates must exist and every value must be present
 * and well formed, so a bad parameter never leaves one agent rendered and the other stale.
 */
public final class AgentRenderer {

  /** Directory of the persona agent under {@code .agents/}. */
  static final String PERSONA = "persona";

  /** Directory of the mediator agent under {@code .agents/}. */
  static final String MEDIATOR = "mediador";

  /** Template file name inside each agent directory. */
  static final String TEMPLATE = "agent.yaml.tmpl";

  /** Rendered file name inside each agent directory. */
  static final String OUTPUT = "agent.yaml";

  /**
   * A model is a single plain YAML scalar: no spaces, quotes, newlines or YAML indicators, so it
   * cannot inject keys into the rendered {@code agent.yaml}.
   */
  private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][\\w.:/-]*");

  private AgentRenderer() {}

  /**
   * Writes {@code nexussyncDir/.agents/persona/agent.yaml} and {@code
   * nexussyncDir/.agents/mediador/agent.yaml} from their templates.
   *
   * @param nexussyncDir root of the nexussync directory ({@code ARKANNIE_HOME})
   * @param p model and timeout of each agent
   * @throws AnnException {@code RENDER} if a template is missing, a model is absent or malformed, a
   *     timeout is not positive, a template placeholder has no value or a file cannot be written;
   *     {@code NOT_FOUND} if a template disappears between the check and the read
   * @implNote O(t) time and space, t = total length of the two templates.
   */
  public static void render(Path nexussyncDir, AgentsParams p) throws AnnException {
    Objects.requireNonNull(nexussyncDir, "nexussyncDir");
    Objects.requireNonNull(p, "p");
    Map<String, Map<String, String>> byAgent = new LinkedHashMap<>();
    byAgent.put(PERSONA, values(PERSONA, p.personaModel(), p.personaTimeout()));
    byAgent.put(MEDIATOR, values(MEDIATOR, p.mediatorModel(), p.mediatorTimeout()));
    Path agents = nexussyncDir.resolve(".agents");
    for (String agent : byAgent.keySet()) {
      requireTemplate(agents.resolve(agent).resolve(TEMPLATE));
    }
    for (Map.Entry<String, Map<String, String>> entry : byAgent.entrySet()) {
      Path dir = agents.resolve(entry.getKey());
      ProgramRenderer.render(dir.resolve(TEMPLATE), entry.getValue(), dir.resolve(OUTPUT));
    }
  }

  private static Map<String, String> values(String agent, String model, int timeout)
      throws AnnException {
    if (model == null || !MODEL.matcher(model).matches()) {
      throw new AnnException(
          AnnException.Kind.RENDER, "key model of agent " + agent + " has no valid value");
    }
    if (timeout <= 0) {
      throw new AnnException(
          AnnException.Kind.RENDER, "key timeout of agent " + agent + " must be positive");
    }
    return Map.of("model", model, "timeout", Integer.toString(timeout));
  }

  private static void requireTemplate(Path template) throws AnnException {
    if (!Files.isRegularFile(template)) {
      throw new AnnException(AnnException.Kind.RENDER, "agent template not found: " + template);
    }
  }
}
