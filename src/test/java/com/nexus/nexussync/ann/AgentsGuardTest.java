package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentsGuardTest {

  @TempDir Path home;

  private Path agent(String name, String yamlFile) throws IOException {
    Path dir = Files.createDirectories(home.resolve(".agents").resolve(name));
    Files.writeString(dir.resolve(yamlFile), "name: " + name + "\n");
    Files.writeString(dir.resolve("harness.md"), "# " + name + "\n");
    return dir;
  }

  private void assertAgentsError(String inMessage) {
    assertThatThrownBy(() -> AgentsGuard.check(home))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.AGENTS))
        .hasMessageContaining(inMessage);
  }

  // U6-13
  @Test
  void givenExactlyPersonaAndMediador_whenCheck_thenPasses() throws Exception {
    agent("persona", "agent.yaml.tmpl");
    agent("mediador", "agent.yaml");
    Files.writeString(home.resolve(".agents/README.md"), "not an agent");

    assertThatCode(() -> AgentsGuard.check(home)).doesNotThrowAnyException();
  }

  // U6-13
  @Test
  void givenExtraAgent_whenCheck_thenAgentsErrorNamingIt() throws Exception {
    agent("persona", "agent.yaml");
    agent("mediador", "agent.yaml");
    agent("nova", "agent.yaml");

    assertAgentsError("nova");
  }

  // U6-13
  @Test
  void givenMissingMediador_whenCheck_thenAgentsErrorNamingIt() throws Exception {
    agent("persona", "agent.yaml");

    assertAgentsError("mediador");
  }

  @Test
  void givenNoAgentsDirectory_whenCheck_thenAgentsError() {
    assertAgentsError("mediador");
  }

  @Test
  void givenAgentWithoutYaml_whenCheck_thenAgentsErrorNamingIt() throws Exception {
    agent("persona", "agent.yaml");
    Path mediador = agent("mediador", "agent.yaml");
    Files.delete(mediador.resolve("agent.yaml"));

    assertAgentsError("mediador");
  }

  @Test
  void givenAgentWithoutHarness_whenCheck_thenAgentsErrorNamingIt() throws Exception {
    Path persona = agent("persona", "agent.yaml");
    agent("mediador", "agent.yaml");
    Files.delete(persona.resolve("harness.md"));

    assertAgentsError("persona");
  }
}
