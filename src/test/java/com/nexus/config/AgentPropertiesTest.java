package com.nexus.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.config.AgentProperties.TimePolicy;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** [t06b-agente-java] U13.5 - Propiedades nuevas del agente: valores por omision y validacion al arrancar. */
class AgentPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AgentProperties.class)
    static class Enabled {}

    /** Solo las propiedades que ya existian antes de t06b. */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Enabled.class)
            .withPropertyValues(
                    "nexus.agent.ollama-url=http://localhost:11434",
                    "nexus.agent.chat-model=mistral:7b",
                    "nexus.agent.embed-model=bge-m3",
                    "nexus.agent.qdrant-url=http://localhost:6333",
                    "nexus.agent.llm-timeout=7s");

    @Test
    @DisplayName("U13.5: should load the defaults of the new properties")
    void shouldLoadTheDefaultsOfTheNewProperties() {
        runner.run(context -> {
            AgentProperties p = context.getBean(AgentProperties.class);
            assertThat(p.enabled()).isFalse();
            assertThat(p.alias()).isEqualTo("nexus_catalog_active");
            assertThat(p.textVersion()).isEqualTo("t2");
            assertThat(p.budget()).isEqualTo(Duration.ofSeconds(9));
            assertThat(p.minLlm()).isEqualTo(Duration.ofMillis(2500));
            assertThat(p.timePolicy()).isEqualTo(TimePolicy.HARD);
            assertThat(p.topK()).isEqualTo(30);
            assertThat(p.llmPicks()).isEqualTo(5);
            assertThat(p.numCtx()).isEqualTo(8192);
            assertThat(p.numPredict()).isEqualTo(600);
            assertThat(p.temperature()).isEqualTo(0.2);
            assertThat(p.maxAttempts()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("U13.5: should fail to start with an invalid time policy")
    void shouldFailToStartWithAnInvalidTimePolicy() {
        runner.withPropertyValues("nexus.agent.time-policy=AVECES")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("U13.5: should fail to start with llm-picks below 1")
    void shouldFailToStartWithLlmPicksBelowOne() {
        runner.withPropertyValues("nexus.agent.llm-picks=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("U13.5: should accept the SOFT policy for local measurements")
    void shouldAcceptTheSoftPolicy() {
        runner.withPropertyValues("nexus.agent.time-policy=SOFT")
                .run(context -> assertThat(context.getBean(AgentProperties.class).timePolicy())
                        .isEqualTo(TimePolicy.SOFT));
    }
}
