package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.model.WeatherCondition;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [rango-distancia] U1 - La distancia elegida viaja en la consulta y nunca se relaja. */
class RecommendationQueryTest {

    private static final Instant START = Instant.parse("2026-11-02T22:00:00Z");
    private static final Instant END = Instant.parse("2026-11-03T01:00:00Z");
    private static final List<Coordinates> REFS = List.of(new Coordinates(19.43, -99.13));

    private static RecommendationQuery query() {
        return new RecommendationQuery(Scope.ANY, 500, Set.of(IdeaCategory.values()[0]), START, END, REFS);
    }

    @Test
    @DisplayName("should search within 15 km when built without a distance")
    void shouldSearchWithin15KmWhenBuiltWithoutADistance() {
        assertThat(query().radiusKm()).isEqualTo(15);
        assertThat(new RecommendationQuery(Scope.ANY, 500, (IdeaCategory) null, START, END, REFS).radiusKm())
                .isEqualTo(15);
    }

    @Test
    @DisplayName("should keep the chosen distance when the ladder relaxes cost, scope or category and when the weather is added")
    void shouldKeepTheChosenDistanceWhenTheLadderRelaxes() {
        RecommendationQuery q = query().withRadiusKm(3);

        assertThat(q.radiusKm()).isEqualTo(3);
        assertThat(q.withMaxCost(1_000).radiusKm()).isEqualTo(3);
        assertThat(q.withScope(Scope.CITY).radiusKm()).isEqualTo(3);
        assertThat(q.withAnyCategory().radiusKm()).isEqualTo(3);
        assertThat(q.withWeather(WeatherCondition.RAINY).radiusKm()).isEqualTo(3);
        assertThat(q.withWeather(WeatherCondition.RAINY).withRadiusKm(5).weather()).isEqualTo(WeatherCondition.RAINY);
    }

    @Test
    @DisplayName("should reject a non positive distance")
    void shouldRejectANonPositiveDistance() {
        assertThatThrownBy(() -> query().withRadiusKm(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
