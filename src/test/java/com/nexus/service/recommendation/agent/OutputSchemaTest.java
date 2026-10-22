package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [t06b-agente-java] U8 - Esquema JSON que se manda a Ollama como {@code format}. */
class OutputSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("U8.1: should limit ref to 1..n, picks to k and why to 20..200 with closed objects")
    void shouldLimitRefPicksAndWhyWithClosedObjects() {
        JsonNode schema = mapper.valueToTree(OutputSchema.of(30, 5));

        JsonNode picks = schema.path("properties").path("picks");
        JsonNode item = picks.path("items");
        assertThat(schema.path("required").get(0).asText()).isEqualTo("picks");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(picks.path("minItems").asInt()).isEqualTo(1);
        assertThat(picks.path("maxItems").asInt()).isEqualTo(5);
        assertThat(item.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(item.path("properties").path("ref").path("enum"))
                .extracting(JsonNode::asInt)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 30).boxed().toList());
        assertThat(item.path("properties").path("why").path("minLength").asInt()).isEqualTo(20);
        assertThat(item.path("properties").path("why").path("maxLength").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("U8.2: should cap maxItems at n when k is larger")
    void shouldCapMaxItemsAtNWhenKIsLarger() {
        JsonNode schema = mapper.valueToTree(OutputSchema.of(3, 5));

        assertThat(schema.path("properties").path("picks").path("maxItems").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("U8.3: should reject n or k below 1")
    void shouldRejectNOrKBelowOne() {
        assertThatThrownBy(() -> OutputSchema.of(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputSchema.of(30, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
