package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link AesEncryptionService} (RNF-11: cifrado en reposo de datos
 * sensibles -- registro emocional, calificaciones de satisfaccion). Distinto de
 * {@link JwtService}/BCrypt: esto es cifrado reversible (2.4.8), no hashing.
 *
 * <p>Los secretos de esta clase son literales de prueba, no credenciales reales.
 */
class AesEncryptionServiceTest {

    private static final String SECRET = "test-secret-para-aesencryptionservicetest";

    private static final String OTHER_SECRET = "otro-secreto-completamente-distinto-00000";

    @Test
    @DisplayName("should recover the original plaintext when round-tripped")
    void shouldRecoverTheOriginalPlaintextWhenRoundTripped() {
        AesEncryptionService service = new AesEncryptionService(new AesProperties(SECRET));

        String ciphertext = service.encrypt("valencia=0.8;activacion=0.6");

        assertThat(service.decrypt(ciphertext)).isEqualTo("valencia=0.8;activacion=0.6");
    }

    @Test
    @DisplayName("should produce different ciphertext for the same plaintext on each call")
    void shouldProduceDifferentCiphertextForTheSamePlaintextOnEachCall() {
        AesEncryptionService service = new AesEncryptionService(new AesProperties(SECRET));

        String first = service.encrypt("dato");
        String second = service.encrypt("dato");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("should not leak the plaintext inside the ciphertext")
    void shouldNotLeakThePlaintextInsideTheCiphertext() {
        AesEncryptionService service = new AesEncryptionService(new AesProperties(SECRET));

        String ciphertext = service.encrypt("dato-secreto-reconocible");

        assertThat(ciphertext).doesNotContain("dato-secreto-reconocible");
    }

    @Test
    @DisplayName("should reject decryption with a different key")
    void shouldRejectDecryptionWithADifferentKey() {
        AesEncryptionService encryptor = new AesEncryptionService(new AesProperties(SECRET));
        AesEncryptionService otherDecryptor = new AesEncryptionService(new AesProperties(OTHER_SECRET));

        String ciphertext = encryptor.encrypt("dato");

        assertThatThrownBy(() -> otherDecryptor.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should reject a tampered ciphertext instead of silently returning garbage")
    void shouldRejectATamperedCiphertextInsteadOfSilentlyReturningGarbage() {
        AesEncryptionService service = new AesEncryptionService(new AesProperties(SECRET));
        String ciphertext = service.encrypt("dato");
        char last = ciphertext.charAt(ciphertext.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        String tampered = ciphertext.substring(0, ciphertext.length() - 1) + replacement;

        assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
