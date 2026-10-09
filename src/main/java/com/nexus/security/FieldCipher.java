package com.nexus.security;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado de campo AES-256-GCM para datos sensibles en reposo (RNF-11, nexus-EMO-01).
 *
 * <p>Formato del blob: {@code [0x01][IV aleatorio de 12][cifrado+etiqueta de 16]}. Cada llamada usa
 * un IV nuevo de {@link SecureRandom}, así que cifrar dos veces el mismo texto da blobs distintos.
 * La AAD (dato adicional autenticado) liga el blob a su columna: descifrar con otra AAD falla, de
 * modo que un ciphertext no puede trasplantarse a otra columna.
 *
 * <p>La clave nunca se registra: {@link #toString()} la enmascara y la clase no la imprime en
 * ningún log. El único aviso que emite es cuando la clave es todo ceros (clave de desarrollo).
 */
@Component
public class FieldCipher {

    private static final Logger log = LoggerFactory.getLogger(FieldCipher.class);

    static final int KEY_BYTES = 32;
    static final int IV_BYTES = 12;
    static final int TAG_BITS = 128;
    static final byte VERSION_1 = 0x01;
    static final int OVERHEAD_BYTES = 1 + IV_BYTES + TAG_BITS / 8; // 29

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key clave de 32 bytes codificada en base64, de {@code nexus.crypto.data-key}.
     * @throws IllegalArgumentException si la clave no es base64 o no decodifica a 32 bytes. El
     *     mensaje nombra la propiedad y la longitud medida, nunca el valor.
     */
    public FieldCipher(@Value("${nexus.crypto.data-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nexus.crypto.data-key no es base64 valido");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "nexus.crypto.data-key debe decodificar a "
                            + KEY_BYTES
                            + " bytes; decodifica a "
                            + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
        if (isAllZeros(raw)) {
            log.warn(
                    "nexus.crypto.data-key es todo ceros: clave de desarrollo, no apta para produccion");
        }
    }

    /**
     * @return {@code [0x01][IV 12][cifrado+etiqueta]}.
     * @implNote O(n) en tiempo y espacio sobre la longitud del texto.
     */
    public byte[] encrypt(byte[] plaintext, byte[] aad) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] blob = new byte[OVERHEAD_BYTES + plaintext.length];
            blob[0] = VERSION_1;
            System.arraycopy(iv, 0, blob, 1, IV_BYTES);
            System.arraycopy(ciphertext, 0, blob, 1 + IV_BYTES, ciphertext.length);
            return blob;
        } catch (GeneralSecurityException e) {
            throw new FieldCipherException("no se pudo cifrar el campo", e);
        }
    }

    /**
     * @throws FieldCipherException si la versión no es {@code 0x01}, el blob mide menos que
     *     {@link #OVERHEAD_BYTES}, o la etiqueta no verifica (manipulado, otra clave u otra AAD).
     */
    public byte[] decrypt(byte[] blob, byte[] aad) {
        if (blob == null || blob.length < OVERHEAD_BYTES) {
            throw new FieldCipherException("blob cifrado demasiado corto");
        }
        if (blob[0] != VERSION_1) {
            throw new FieldCipherException("version de cifrado desconocida");
        }
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(blob, 1, iv, 0, IV_BYTES);
        int ciphertextLength = blob.length - 1 - IV_BYTES;
        byte[] ciphertext = new byte[ciphertextLength];
        System.arraycopy(blob, 1 + IV_BYTES, ciphertext, 0, ciphertextLength);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new FieldCipherException("el campo cifrado no verifica", e);
        }
    }

    @Override
    public String toString() {
        return "FieldCipher[key=****]";
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
