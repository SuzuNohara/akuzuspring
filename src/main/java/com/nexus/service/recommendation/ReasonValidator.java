package com.nexus.service.recommendation;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * [T-06] U8 - Filtro de las frases del modelo antes de mostrarlas (R10, RN-32): cortas, sin enlaces
 * y sin hablar del animo de nadie (RN-28). Si una no pasa, se usa la del catalogo.
 */
public final class ReasonValidator {

    private static final Pattern LINK = Pattern.compile("(https?://|www\\.)", Pattern.CASE_INSENSITIVE);

    /** Raices (sin acentos, en minusculas) de palabras sobre el animo que no deben salir. */
    private static final List<String> MOOD_WORDS = List.of(
            "animo", "emocion", "triste", "deprim", "ansie", "ansios", "estres", "enoj", "agotad",
            "cansad", "bajon", "sienten", "siente", "sientes", "humor", "estado de");

    private ReasonValidator() {}

    public static boolean isAcceptable(String reason) {
        if (reason == null || reason.isBlank() || reason.strip().length() > CatalogReasonWriter.MAX_LENGTH) {
            return false;
        }
        if (LINK.matcher(reason).find()) {
            return false;
        }
        String plain = Normalizer.normalize(reason, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return MOOD_WORDS.stream().noneMatch(plain::contains);
    }
}
