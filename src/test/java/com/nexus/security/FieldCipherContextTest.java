package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** El backend no debe arrancar sin una clave de datos válida (nexus-EMO-01, R6). */
class FieldCipherContextTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(FieldCipher.class);

    @Test
    @DisplayName("should fail context startup when data key is missing")
    void shouldFailContextStartupWhenDataKeyIsMissing() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("should start context when data key is valid")
    void shouldStartContextWhenDataKeyIsValid() {
        String key = Base64.getEncoder().encodeToString(new byte[FieldCipher.KEY_BYTES]);

        runner.withPropertyValues("nexus.crypto.data-key=" + key)
                .run(context -> assertThat(context).hasSingleBean(FieldCipher.class));
    }
}
