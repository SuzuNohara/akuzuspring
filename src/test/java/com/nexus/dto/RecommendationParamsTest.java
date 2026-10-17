package com.nexus.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.model.Scope;
import com.nexus.model.ZoneType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A8 - Solo valores cerrados: obligatorios presentes y presupuesto entre 0 y 15 000. */
class RecommendationParamsTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Instant START = Instant.parse("2026-10-18T01:00:00Z");

    private static Set<ConstraintViolation<RecommendationParams>> check(ZoneType zone, Scope scope, int cost, Instant start) {
        return VALIDATOR.validate(new RecommendationParams(zone, null, null, null, scope, cost, null, start, START.plusSeconds(3600)));
    }

    @Test
    @DisplayName("parametros completos son validos")
    void valid() {
        assertThat(check(ZoneType.NONE, Scope.ANY, 1500, START)).isEmpty();
        assertThat(check(ZoneType.NONE, Scope.ANY, 0, START)).isEmpty();
        assertThat(check(ZoneType.NONE, Scope.ANY, 15_000, START)).isEmpty();
    }

    @Test
    @DisplayName("presupuesto fuera de 0..15 000 es invalido")
    void budgetOutOfRange() {
        assertThat(check(ZoneType.NONE, Scope.ANY, -1, START)).hasSize(1);
        assertThat(check(ZoneType.NONE, Scope.ANY, 15_001, START)).hasSize(1);
    }

    @Test
    @DisplayName("zona, alcance e inicio son obligatorios")
    void requiredFields() {
        assertThat(check(null, Scope.ANY, 0, START)).hasSize(1);
        assertThat(check(ZoneType.NONE, null, 0, START)).hasSize(1);
        assertThat(check(ZoneType.NONE, Scope.ANY, 0, null)).hasSize(1);
    }
}
