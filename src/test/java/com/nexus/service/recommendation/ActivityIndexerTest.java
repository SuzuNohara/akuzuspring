package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.entity.Activity;
import com.nexus.integration.AgentTestProperties;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantPoint;
import com.nexus.repository.ActivityRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** [T-06] U5 - I1 a I3: el catalogo se vectoriza con prefijo y se sube solo si hace falta. */
class ActivityIndexerTest {

    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final QdrantClient qdrant = mock(QdrantClient.class);
    private final ActivityIndexer indexer =
            new ActivityIndexer(activities, ollama, qdrant, AgentTestProperties.defaults());

    private static Activity dinner() {
        return Activity.builder()
                .id(7L)
                .title("Cena en casa")
                .description("Cocinen juntos una receta nueva.")
                .rationale("Un plan tranquilo para platicar.")
                .activityType("DINNER")
                .locationScope("HOME")
                .durationMin(90)
                .dayparts("AFTERNOON|NIGHT")
                .costMxnPp(250)
                .outdoor(false)
                .build();
    }

    @Test
    @DisplayName("I1: arma el texto con prefijo y sube cada actividad con sus atributos")
    void indexesEveryActivityWithPrefixAndPayload() {
        when(activities.findAll()).thenReturn(List.of(dinner()));
        when(ollama.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f, 0.3f});

        int indexed = indexer.reindexAll();

        assertThat(indexed).isEqualTo(1);
        verify(ollama).embed(ActivityIndexer.documentText(dinner()));
        assertThat(ActivityIndexer.documentText(dinner()))
                .startsWith("search_document: Cena en casa.")
                .contains("Cocinen juntos", "Un plan tranquilo", "Emocional", "en casa", "bajo techo");
        verify(qdrant).ensureCollection("activities", 3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QdrantPoint>> points = ArgumentCaptor.forClass(List.class);
        verify(qdrant).upsert(eq("activities"), points.capture());
        QdrantPoint point = points.getValue().get(0);
        assertThat(point.id()).isEqualTo("7");
        assertThat(point.payload())
                .containsEntry("dimension", "EMOCIONAL")
                .containsEntry("location_scope", "HOME")
                .containsEntry("cost_mxn_pp", 250)
                .containsEntry("duration_min", 90)
                .containsEntry("dayparts", List.of("AFTERNOON", "NIGHT"))
                .containsEntry("is_outdoor", false);
    }

    @Test
    @DisplayName("I2: no reindexa si Qdrant ya tiene el mismo numero de actividades")
    void skipsWhenInSync() {
        when(activities.count()).thenReturn(80L);
        when(qdrant.count("activities")).thenReturn(80L);

        assertThat(indexer.ensureIndexed()).isZero();
        verify(ollama, never()).embed(anyString());
    }

    @Test
    @DisplayName("I3: reindexa si los conteos no coinciden")
    void reindexesWhenOutOfSync() {
        when(activities.count()).thenReturn(1L);
        when(qdrant.count("activities")).thenReturn(0L);
        when(activities.findAll()).thenReturn(List.of(dinner()));
        when(ollama.embed(anyString())).thenReturn(new float[] {1f});

        assertThat(indexer.ensureIndexed()).isEqualTo(1);
    }
}
