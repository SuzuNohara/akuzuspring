package com.nexus.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas unitarias de {@link LinkCodeGenerator}.
 *
 * <p>Sujeto elegido por ser la única clase del proyecto sin dependencias, sin estado y sin
 * contexto Spring: sirve para demostrar que el arnés funciona sin invadir el alcance de
 * {@code nexus-TEST-04}, que cubre las reglas de negocio con consecuencia.
 */
class LinkCodeGeneratorTest {

  /** Longitud total del código: 8 caracteres más el guion separador. */
  private static final int CODE_LENGTH_WITH_SEPARATOR = 9;

  /** Número de generaciones del caso de dispersión. */
  private static final int SAMPLE_SIZE = 1_000;

  /**
   * Mínimo de valores distintos exigido sobre {@link #SAMPLE_SIZE} generaciones. Se tolera una
   * colisión: el espacio es 36^8, luego la probabilidad de dos o más colisiones es despreciable
   * pero no nula, y un test que falle una vez al año es peor que un test con margen.
   */
  private static final int MIN_DISTINCT = 999;

  @Test
  @DisplayName("should return a nine character code when generated")
  void shouldReturnNineCharCodeWhenGenerated() {
    String code = LinkCodeGenerator.generate();

    assertThat(code).hasSize(CODE_LENGTH_WITH_SEPARATOR);
  }

  @Test
  @DisplayName("should match the XXXX-XXXX pattern when generated")
  void shouldMatchXxxxDashXxxxPatternWhenGenerated() {
    String code = LinkCodeGenerator.generate();

    assertThat(code).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}$");
  }

  @Test
  @DisplayName("should return distinct codes when generated repeatedly")
  void shouldReturnDistinctCodesWhenGeneratedRepeatedly() {
    Set<String> codes = new HashSet<>();

    for (int i = 0; i < SAMPLE_SIZE; i++) {
      codes.add(LinkCodeGenerator.generate());
    }

    assertThat(codes).hasSizeGreaterThanOrEqualTo(MIN_DISTINCT);
  }

  @Test
  @DisplayName("should accept a well formed code when validating format")
  void shouldAcceptWellFormedCodeWhenValidatingFormat() {
    assertThat(LinkCodeGenerator.isValidFormat("AB7K-M9P2")).isTrue();
    assertThat(LinkCodeGenerator.isValidFormat(LinkCodeGenerator.generate())).isTrue();
  }

  @Test
  @DisplayName("should reject null when validating format")
  void shouldRejectNullWhenValidatingFormat() {
    assertThat(LinkCodeGenerator.isValidFormat(null)).isFalse();
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @DisplayName("should reject malformed codes when validating format")
  @ValueSource(strings = {"", "AB7KM9P2", "ab7k-m9p2", "AB7K-M9P2X", "AB7-KM9P2"})
  void shouldRejectMalformedCodesWhenValidatingFormat(String malformed) {
    assertThat(LinkCodeGenerator.isValidFormat(malformed)).isFalse();
  }
}
