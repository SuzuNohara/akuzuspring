package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.entity.Activity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

/**
 * [t06b-agente-java] U1 / R2 - Los intereses de una actividad son diferidos: el banco de ideas y las
 * recomendaciones siguen con las mismas consultas, y solo el indexador los trae con un JOIN FETCH.
 *
 * <p>Revisa el mapeo y las consultas sin base de datos: los *IT de repositorio necesitan Docker
 * (Testcontainers) y esta laptop no lo tiene.
 */
class ActivityRepositoryTest {

    @Test
    @DisplayName("U1: should map the interests as a lazy many-to-many over activity_interests")
    void shouldMapTheInterestsAsALazyManyToManyOverActivityInterests() throws Exception {
        var field = Activity.class.getDeclaredField("interests");

        assertThat(field.getAnnotation(ManyToMany.class).fetch()).isEqualTo(FetchType.LAZY);
        JoinTable join = field.getAnnotation(JoinTable.class);
        assertThat(join.name()).isEqualTo("activity_interests");
        assertThat(join.joinColumns()[0].name()).isEqualTo("activity_id");
        assertThat(join.inverseJoinColumns()[0].name()).isEqualTo("preference_id");
    }

    @Test
    @DisplayName("U1: should not fetch the interests in the idea bank queries")
    void shouldNotFetchTheInterestsInTheIdeaBankQueries() throws Exception {
        for (Method method : new Method[] {
            ActivityRepository.class.getMethod("findAllByOrderByTitleAsc"),
            ActivityRepository.class.getMethod("findByActivityTypeInOrderByTitleAsc", java.util.Collection.class)
        }) {
            assertThat(method.getAnnotation(EntityGraph.class)).isNull();
            assertThat(method.getAnnotation(Query.class)).isNull();
        }
    }

    @Test
    @DisplayName("U1: should fetch the interests in one query only for indexing")
    void shouldFetchTheInterestsInOneQueryOnlyForIndexing() throws Exception {
        Query query = ActivityRepository.class.getMethod("findAllWithInterests").getAnnotation(Query.class);

        assertThat(query.value()).containsIgnoringCase("left join fetch a.interests").containsIgnoringCase("distinct");
    }
}
