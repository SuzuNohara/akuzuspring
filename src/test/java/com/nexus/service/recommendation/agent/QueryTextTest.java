package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.service.recommendation.CoupleContext;
import com.nexus.service.recommendation.MoodLevel;
import com.nexus.service.recommendation.RecommendationQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [t06b-agente-java] U5 - Texto de la consulta: vocabulario cerrado, sin datos de una sola persona. */
class QueryTextTest {

    private static final Instant START = Instant.parse("2026-10-24T01:00:00Z");

    static RecommendationQuery query(Scope scope, Set<IdeaCategory> categories) {
        return new RecommendationQuery(scope, 500, categories, START, START.plusSeconds(3 * 3600), List.of());
    }

    static CoupleContext context(List<String> mine, List<String> partner) {
        return new CoupleContext(
                Map.of(IdeaCategory.SOCIAL, 3, IdeaCategory.FISICO, 1), MoodLevel.BAJO, MoodLevel.ALTO, mine, partner);
    }

    @Test
    @DisplayName("U5.1: should put shared interests first, then yours and your partner's, max 5 each, sorted")
    void shouldPutSharedInterestsFirstThenEachOneMaxFiveSorted() {
        List<String> mine = List.of("Yoga", "Cine", "Museos", "Ajedrez", "Café", "Teatro", "Ópera", "Baile");
        List<String> partner = List.of("Cine", "Senderismo", "Yoga", "Ciclismo");

        String text = QueryText.of(query(Scope.ANY, Set.of()), context(mine, partner));

        assertThat(text.lines().toList().get(1)).isEqualTo(
                "Intereses compartidos: Cine, Yoga. Tus intereses: Ajedrez, Baile, Café, Museos, Ópera. "
                        + "Intereses de tu pareja: Ciclismo, Senderismo.");
    }

    @Test
    @DisplayName("U5.2: should write ninguno without interests")
    void shouldWriteNingunoWithoutInterests() {
        String text = QueryText.of(query(Scope.HOME, Set.of()), context(List.of(), List.of()));

        assertThat(text.lines().toList().get(1))
                .isEqualTo("Intereses compartidos: ninguno. Tus intereses: ninguno. Intereses de tu pareja: ninguno.");
    }

    @Test
    @DisplayName("U5.3: should write only the combined mood, and desconocido without data")
    void shouldWriteOnlyTheCombinedMood() {
        String known = QueryText.of(query(Scope.HOME, Set.of()), context(List.of(), List.of()));
        String unknown = QueryText.of(query(Scope.HOME, Set.of()), CoupleContext.empty());

        assertThat(known.lines().toList().get(2)).isEqualTo("Ánimo de la pareja: ánimo bajo, energía alta.");
        assertThat(unknown.lines().toList().get(2))
                .isEqualTo("Ánimo de la pareja: ánimo desconocido, energía desconocida.");
    }

    @Test
    @DisplayName("U5.4: should never contain mood numbers nor talk about one person's mood")
    void shouldNeverContainMoodNumbersNorOnePersonsMood() {
        String text = QueryText.of(
                query(Scope.CITY, Set.of(IdeaCategory.SOCIAL)), context(List.of("Cine"), List.of("Yoga")));

        assertThat(text).doesNotContainPattern("\\d").doesNotContain("Ánimo de A", "Ánimo de B", "tu ánimo");
        assertThat(text.lines()).hasSize(4);
    }

    @Test
    @DisplayName("U5.5: should name the requested category with the scope, or only the scope")
    void shouldNameTheRequestedCategoryWithTheScopeOrOnlyTheScope() {
        String withCategory = QueryText.of(
                query(Scope.CITY, Set.of(IdeaCategory.SOCIAL, IdeaCategory.FISICO)), CoupleContext.empty());
        String without = QueryText.of(query(Scope.ANY, Set.of()), CoupleContext.empty());

        assertThat(withCategory.lines().findFirst())
                .contains("Pareja busca: Bienestar Físico, Bienestar Social. Alcance: en la ciudad.");
        assertThat(without.lines().findFirst()).contains("Pareja busca: donde sea.");
    }

    @Test
    @DisplayName("U5: should list the couple's dimensions by weight in the last line")
    void shouldListTheCouplesDimensionsByWeight() {
        String text = QueryText.of(query(Scope.HOME, Set.of()), context(List.of(), List.of()));
        String none = QueryText.of(query(Scope.HOME, Set.of()), CoupleContext.empty());

        assertThat(text.lines().toList().get(3)).isEqualTo("Bienestar: Bienestar Social, Bienestar Físico.");
        assertThat(none.lines().toList().get(3)).isEqualTo("Bienestar: ninguno.");
    }
}
