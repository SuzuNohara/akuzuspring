package com.nexus.entity;

import com.nexus.security.FieldCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;

/**
 * Cifra el código de ánimo antes de guardarlo y lo descifra al leerlo (nexus-EMO-01, RNF-11).
 *
 * <p>Rellena el código con espacios hasta {@link #PADDED_LENGTH} antes de cifrar, para que la
 * longitud del blob (siempre 61 bytes) no delate qué opción es. La {@link #AAD} liga el cifrado a
 * esta columna: un blob de otra columna no descifra aquí.
 */
@Converter
public class MoodCodeConverter implements AttributeConverter<String, byte[]> {

    static final int PADDED_LENGTH = 32;
    static final byte[] AAD = "nexus:mood_entries.mood_cipher".getBytes(StandardCharsets.US_ASCII);

    private final FieldCipher cipher;

    public MoodCodeConverter(FieldCipher cipher) {
        this.cipher = cipher;
    }

    /**
     * @throws IllegalArgumentException si el código mide más de {@link #PADDED_LENGTH} o no es ASCII.
     */
    @Override
    public byte[] convertToDatabaseColumn(String code) {
        if (code == null) {
            return null;
        }
        if (code.length() > PADDED_LENGTH) {
            throw new IllegalArgumentException(
                    "el codigo de animo no puede exceder " + PADDED_LENGTH + " caracteres");
        }
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) > 0x7F) {
                throw new IllegalArgumentException("el codigo de animo debe ser ASCII");
            }
        }
        String padded = code + " ".repeat(PADDED_LENGTH - code.length());
        return cipher.encrypt(padded.getBytes(StandardCharsets.US_ASCII), AAD);
    }

    @Override
    public String convertToEntityAttribute(byte[] blob) {
        if (blob == null) {
            return null;
        }
        byte[] plain = cipher.decrypt(blob, AAD);
        return new String(plain, StandardCharsets.US_ASCII).stripTrailing();
    }
}
