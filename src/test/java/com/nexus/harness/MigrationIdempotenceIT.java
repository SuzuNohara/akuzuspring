package com.nexus.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Re-aplica cada fichero de {@link MySqlSchemaSupport#MIGRATIONS} con {@code mysql --force} dentro
 * del contenedor y afirma que TODOS los {@code ERROR nnnn} de la salida estan en {@link
 * MySqlSchemaSupport#TOLERATED}: el contrato exacto de {@code make db-migrate}.
 *
 * <p>Deliberadamente sin contexto Spring: solo el contenedor. {@code isNotEmpty()} importa: una
 * migracion que en la segunda pasada no da ningun error es una migracion que no se aplico la primera
 * vez.
 */
class MigrationIdempotenceIT extends MySqlSchemaSupport {

  @ParameterizedTest(name = "{1}")
  @MethodSource("migrations")
  @DisplayName("should only raise db-migrate tolerated errors when a migration is applied twice")
  void shouldOnlyRaiseToleratedErrorsWhenMigrationIsAppliedTwice(int index, String file) {
    // Given: la migracion ya se aplico una vez al arrancar el contenedor
    // When: se vuelve a aplicar con la misma semantica que db-migrate
    List<String> codes = applyMigration(MYSQL, migrationPath(index, file));
    // Then: hubo errores (si no, la primera pasada no aplico nada) y todos son "ya existe"
    assertThat(codes).isNotEmpty();
    assertThat(codes).allSatisfy(code -> assertThat(TOLERATED).contains(code));
  }

  static Stream<Arguments> migrations() {
    return IntStream.range(0, MIGRATIONS.length).mapToObj(i -> Arguments.of(i + 1, MIGRATIONS[i]));
  }
}
