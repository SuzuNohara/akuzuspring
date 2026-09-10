package com.nexus.harness;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba el arnés, no la aplicación: verifica que failsafe ejecuta los {@code *IT} en la fase
 * {@code integration-test} y que lo hace contra {@code target/classes}.
 *
 * <p>Deliberadamente sin {@code @SpringBootTest}: hoy ningún contexto Spring puede arrancar sin
 * base de datos, porque {@code ddl-auto=none} y las credenciales vienen del entorno.
 */
class HarnessSmokeIT {

  @Test
  @DisplayName("should run under failsafe in the integration-test phase")
  void shouldRunUnderFailsafeWhenVerifyIsInvoked() {
    assertThat(System.getProperty("nexus.harness.phase")).isEqualTo("integration-test");
  }

  @Test
  @DisplayName("should see main classes on the test classpath")
  void shouldSeeMainClassesOnClasspathWhenRunning() throws ClassNotFoundException {
    assertThat(Class.forName("com.nexus.NexusApplication")).isNotNull();
  }
}
