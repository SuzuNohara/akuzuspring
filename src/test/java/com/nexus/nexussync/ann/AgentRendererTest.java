package com.nexus.nexussync.ann;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nexus.nexussync.params.AgentsParams;
import com.nexus.nexussync.params.MediatorClimate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRendererTest {

  private static final String SYNTHETIC =
      "command: \"[x]\"\nmodel: {{model}}\nscope: agnostic\ntimeout: {{timeout}}\n";

  @TempDir Path home;

  private static AgentsParams params(String persona, int pt, String mediator, int mt) {
    return new AgentsParams(persona, pt, mediator, mt, MediatorClimate.AGGREGATED);
  }

  /** Copies the real templates from {@code nexussync.dir} into the temporary home. */
  private void copyRealTemplates() throws IOException {
    String dir = System.getProperty("nexussync.dir", "");
    Path real = Path.of(dir.isEmpty() ? "nexussync-dir-not-set" : dir).resolve(".agents");
    Assumptions.assumeTrue(Files.isDirectory(real), "nexussync.dir not available");
    for (String agent : new String[] {AgentRenderer.PERSONA, AgentRenderer.MEDIATOR}) {
      Path target = home.resolve(".agents").resolve(agent);
      Files.createDirectories(target);
      Files.copy(
          real.resolve(agent).resolve(AgentRenderer.TEMPLATE),
          target.resolve(AgentRenderer.TEMPLATE));
    }
  }

  private void writeTemplate(String agent, String content) throws IOException {
    Path dir = home.resolve(".agents").resolve(agent);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(AgentRenderer.TEMPLATE), content, StandardCharsets.UTF_8);
  }

  private JsonNode rendered(String agent) throws IOException {
    Path file = home.resolve(".agents").resolve(agent).resolve(AgentRenderer.OUTPUT);
    return new YAMLMapper().readTree(file.toFile());
  }

  // U5-01
  @Test
  void givenRealPersonaTemplate_whenRender_thenAgentYamlHasRequestedModelAndTimeout()
      throws Exception {
    copyRealTemplates();

    AgentRenderer.render(home, params("sonnet", 45, "opus", 200));

    JsonNode persona = rendered(AgentRenderer.PERSONA);
    assertThat(persona.get("command").asText()).isEqualTo("[persona]");
    assertThat(persona.get("model").asText()).isEqualTo("sonnet");
    assertThat(persona.get("timeout").asInt()).isEqualTo(45);
    assertThat(persona.get("scope").asText()).isEqualTo("agnostic");
    assertThat(persona.get("default_operation").asText()).isEqualTo("pick");
    assertThat(persona.path("operations").has("vote")).isTrue();
  }

  // U5-02
  @Test
  void givenRealMediatorTemplate_whenRender_thenAgentYamlHasRequestedModelAndTimeout()
      throws Exception {
    copyRealTemplates();

    AgentRenderer.render(home, params("haiku", 90, "claude-sonnet-4.5", 120));

    JsonNode mediator = rendered(AgentRenderer.MEDIATOR);
    assertThat(mediator.get("command").asText()).isEqualTo("[mediador]");
    assertThat(mediator.get("model").asText()).isEqualTo("claude-sonnet-4.5");
    assertThat(mediator.get("timeout").asInt()).isEqualTo(120);
    assertThat(mediator.get("default_operation").asText()).isEqualTo("recommend");
    assertThat(mediator.path("operations").path("recommend").path("grants").get(0).asText())
        .isEqualTo("read");
  }

  @Test
  void givenSyntheticTemplates_whenRender_thenEachAgentGetsItsOwnValues() throws Exception {
    writeTemplate(AgentRenderer.PERSONA, SYNTHETIC);
    writeTemplate(AgentRenderer.MEDIATOR, SYNTHETIC);

    AgentRenderer.render(home, params("a-model", 10, "m-model", 20));

    assertThat(rendered(AgentRenderer.PERSONA).get("model").asText()).isEqualTo("a-model");
    assertThat(rendered(AgentRenderer.PERSONA).get("timeout").asInt()).isEqualTo(10);
    assertThat(rendered(AgentRenderer.MEDIATOR).get("model").asText()).isEqualTo("m-model");
    assertThat(rendered(AgentRenderer.MEDIATOR).get("timeout").asInt()).isEqualTo(20);
  }

  @Test
  void givenMissingMediatorTemplate_whenRender_thenRenderErrorAndNothingWritten() throws Exception {
    writeTemplate(AgentRenderer.PERSONA, SYNTHETIC);

    assertThatThrownBy(() -> AgentRenderer.render(home, params("m", 1, "m", 1)))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.RENDER));
    assertThat(home.resolve(".agents/persona").resolve(AgentRenderer.OUTPUT)).doesNotExist();
  }

  @Test
  void givenNullModel_whenRender_thenRenderError() throws Exception {
    writeTemplate(AgentRenderer.PERSONA, SYNTHETIC);
    writeTemplate(AgentRenderer.MEDIATOR, SYNTHETIC);

    assertRenderError(params(null, 1, "m", 1));
  }

  @Test
  void givenModelThatInjectsYaml_whenRender_thenRenderError() throws Exception {
    writeTemplate(AgentRenderer.PERSONA, SYNTHETIC);
    writeTemplate(AgentRenderer.MEDIATOR, SYNTHETIC);

    assertRenderError(params("m", 1, "haiku\nscope: workspace", 1));
  }

  @Test
  void givenNonPositiveTimeout_whenRender_thenRenderError() throws Exception {
    writeTemplate(AgentRenderer.PERSONA, SYNTHETIC);
    writeTemplate(AgentRenderer.MEDIATOR, SYNTHETIC);

    assertRenderError(params("m", 0, "m", 1));
  }

  @Test
  void givenTemplateWithUnknownKey_whenRender_thenRenderError() throws Exception {
    writeTemplate(AgentRenderer.PERSONA, SYNTHETIC);
    writeTemplate(AgentRenderer.MEDIATOR, SYNTHETIC + "extra: {{unknown}}\n");

    assertRenderError(params("m", 1, "m", 1));
  }

  private void assertRenderError(AgentsParams p) {
    assertThatThrownBy(() -> AgentRenderer.render(home, p))
        .isInstanceOfSatisfying(
            AnnException.class, e -> assertThat(e.kind()).isEqualTo(AnnException.Kind.RENDER));
  }
}
