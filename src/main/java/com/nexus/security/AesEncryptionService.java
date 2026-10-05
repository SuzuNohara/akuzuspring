package com.nexus.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Cifrado simetrico reversible (2.4.8, RNF-11) para datos sensibles que el sistema debe poder
 * mostrar de vuelta al usuario -- registro emocional, calificaciones de satisfaccion. Distinto de
 * {@code BCryptPasswordEncoder} (irreversible, para contraseñas).
 *
 * <p>AES-256-GCM: el modo GCM autentica el texto cifrado ademas de cifrarlo, asi que un dato
 * manipulado o una llave incorrecta fallan al descifrar en vez de devolver basura en silencio. El
 * IV (12 bytes) es aleatorio por cada llamada a {@link #encrypt(String)} y viaja concatenado al
 * inicio del texto cifrado -- no es secreto, solo no debe repetirse con la misma llave.
 *
 * <p>Esta clase no declara logger a proposito: no puede filtrar texto plano ni el secreto a
 * ningun log.
 */
@Service
public class AesEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKey key;

    /**
     * @param properties configuracion de cifrado, validada por el contexto.
     */
    public AesEncryptionService(AesProperties properties) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(properties.secret().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * Cifra un texto plano. Cada llamada usa un IV aleatorio nuevo, asi que dos cifrados del mismo
     * texto producen salidas distintas.
     *
     * @return el IV y el texto cifrado concatenados, codificados en Base64.
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al cifrar", e);
        }
    }

    /**
     * Descifra un valor producido por {@link #encrypt(String)}.
     *
     * @throws IllegalStateException si la llave no coincide con la usada al cifrar, o si el dato
     *     fue alterado -- GCM detecta ambos casos y nunca devuelve texto corrupto.
     */
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Error al descifrar: dato corrupto o llave incorrecta", e);
        }
    }
}
