package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Cifrado, integridad y validación de clave de {@link FieldCipher} (nexus-EMO-01, R5/R6/R7). */
@ExtendWith(OutputCaptureExtension.class)
class FieldCipherTest {

    private static final byte[] AAD = "col:a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OTHER_AAD = "col:b".getBytes(StandardCharsets.US_ASCII);

    private static String keyOf(int fill) {
        byte[] raw = new byte[FieldCipher.KEY_BYTES];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (fill + i);
        }
        return Base64.getEncoder().encodeToString(raw);
    }

    private final FieldCipher cipher = new FieldCipher(keyOf(7));

    @Test
    @DisplayName("should round trip plaintext when aad matches")
    void shouldRoundTripPlaintextWhenAadMatches() {
        byte[] plaintext = "CALM".getBytes(StandardCharsets.US_ASCII);

        byte[] back = cipher.decrypt(cipher.encrypt(plaintext, AAD), AAD);

        assertThat(back).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("should produce different blobs when same plaintext is encrypted twice")
    void shouldProduceDifferentBlobsWhenSamePlaintextIsEncryptedTwice() {
        byte[] plaintext = "CALM".getBytes(StandardCharsets.US_ASCII);

        assertThat(cipher.encrypt(plaintext, AAD)).isNotEqualTo(cipher.encrypt(plaintext, AAD));
    }

    @Test
    @DisplayName("should prefix version and add fixed overhead when encrypting")
    void shouldPrefixVersionAndAddFixedOverheadWhenEncrypting() {
        byte[] plaintext = new byte[32];

        byte[] blob = cipher.encrypt(plaintext, AAD);

        assertThat(blob[0]).isEqualTo(FieldCipher.VERSION_1);
        assertThat(blob).hasSize(FieldCipher.OVERHEAD_BYTES + plaintext.length);
    }

    @Test
    @DisplayName("should reject when one bit is flipped")
    void shouldRejectWhenOneBitIsFlipped() {
        byte[] blob = cipher.encrypt("CALM".getBytes(StandardCharsets.US_ASCII), AAD);
        blob[blob.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(blob, AAD)).isInstanceOf(FieldCipherException.class);
    }

    @Test
    @DisplayName("should reject when aad differs")
    void shouldRejectWhenAadDiffers() {
        byte[] blob = cipher.encrypt("CALM".getBytes(StandardCharsets.US_ASCII), AAD);

        assertThatThrownBy(() -> cipher.decrypt(blob, OTHER_AAD))
                .isInstanceOf(FieldCipherException.class);
    }

    @Test
    @DisplayName("should reject when key differs")
    void shouldRejectWhenKeyDiffers() {
        byte[] blob = cipher.encrypt("CALM".getBytes(StandardCharsets.US_ASCII), AAD);
        FieldCipher other = new FieldCipher(keyOf(99));

        assertThatThrownBy(() -> other.decrypt(blob, AAD)).isInstanceOf(FieldCipherException.class);
    }

    @Test
    @DisplayName("should reject when version is unknown")
    void shouldRejectWhenVersionIsUnknown() {
        byte[] blob = cipher.encrypt("CALM".getBytes(StandardCharsets.US_ASCII), AAD);
        blob[0] = 0x02;

        assertThatThrownBy(() -> cipher.decrypt(blob, AAD)).isInstanceOf(FieldCipherException.class);
    }

    @Test
    @DisplayName("should reject when blob is shorter than overhead")
    void shouldRejectWhenBlobIsShorterThanOverhead() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[FieldCipher.OVERHEAD_BYTES - 1], AAD))
                .isInstanceOf(FieldCipherException.class);
    }

    @Test
    @DisplayName("should reject key when not base64")
    void shouldRejectKeyWhenNotBase64() {
        assertThatThrownBy(() -> new FieldCipher("no-es-base64-!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nexus.crypto.data-key");
    }

    @Test
    @DisplayName("should reject key when not 32 bytes")
    void shouldRejectKeyWhenNot32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new FieldCipher(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("should not reveal key when printed")
    void shouldNotRevealKeyWhenPrinted() {
        String printed = cipher.toString();

        assertThat(printed).isEqualTo("FieldCipher[key=****]");
        assertThat(printed).doesNotContain(keyOf(7));
    }

    @Test
    @DisplayName("should warn when key is all zeros")
    void shouldWarnWhenKeyIsAllZeros(CapturedOutput output) {
        new FieldCipher(Base64.getEncoder().encodeToString(new byte[FieldCipher.KEY_BYTES]));

        assertThat(output).contains("ceros");
    }
}
