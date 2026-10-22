package com.nexus.service.recommendation.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.model.ReasonSource;
import com.nexus.service.recommendation.Candidate;
import com.nexus.service.recommendation.CatalogReasonWriter;
import com.nexus.service.recommendation.ReasonValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * [t06b-agente-java] U11 - Segunda defensa despues del esquema: lee la respuesta de Mistral y decide
 * que se queda (igual que {@code validate.py} del lab).
 *
 * <p>Un {@code ref} malo se descarta; una frase mala se cambia por la del catalogo, asi que una
 * justificacion debil nunca cuesta una recomendacion. Titulos e ids siempre salen de la candidata.
 */
public final class PickValidator {

    /** Largo maximo real de la frase (el esquema deja hasta 200). */
    public static final int WHY_MAX_CHARS = 140;

    private static final Pattern FORBIDDEN = Pattern.compile("(?i)(https?|www\\.|@|[<>{}])");
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1f\\x7f]+");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PickValidator() {}

    /** Una eleccion tal como la escribio el modelo. */
    record RawPick(int ref, String why) {}

    /**
     * @param dropped elecciones descartadas: {@code ref} fuera de rango, repetido o de mas
     * @param replaced frases cambiadas por la del catalogo
     */
    public record ValidationResult(List<WrittenPick> picks, int dropped, int replaced) {}

    /**
     * Valida la respuesta contra las candidatas que vio Mistral. JSON roto o sin {@code picks} se toma
     * como respuesta vacia.
     *
     * @param shown candidatas en el orden en que se numeraron (ref 1 = primera)
     * @param k cuantas elecciones se aceptan como maximo
     * @implNote O(p) con p elecciones.
     */
    public static ValidationResult validate(String json, List<Candidate> shown, int k) {
        List<WrittenPick> picks = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int dropped = 0;
        int replaced = 0;
        for (RawPick pick : parse(json)) {
            boolean inRange = pick.ref() >= 1 && pick.ref() <= shown.size();
            if (!inRange || !seen.add(pick.ref()) || picks.size() >= k) {
                dropped++;
                continue;
            }
            Candidate candidate = shown.get(pick.ref() - 1);
            String why = clean(pick.why());
            if (isValid(why)) {
                picks.add(new WrittenPick(candidate, pick.ref(), why, ReasonSource.AGENT));
            } else {
                replaced++;
                String catalog = CatalogReasonWriter.reasonOf(candidate.activity());
                picks.add(new WrittenPick(candidate, pick.ref(), catalog, ReasonSource.CATALOG));
            }
        }
        return new ValidationResult(List.copyOf(picks), dropped, replaced);
    }

    /** Elementos sin {@code ref} entero se ignoran ({@code "3"} y {@code true} incluidos); sin {@code why}, frase vacia. */
    static List<RawPick> parse(String json) {
        JsonNode doc;
        try {
            doc = json == null ? null : MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return List.of();
        }
        if (doc == null || !doc.path("picks").isArray()) {
            return List.of();
        }
        List<RawPick> picks = new ArrayList<>();
        for (JsonNode item : doc.path("picks")) {
            JsonNode ref = item.path("ref");
            if (ref.isIntegralNumber() && ref.canConvertToInt()) {
                JsonNode why = item.path("why");
                picks.add(new RawPick(ref.asInt(), why.isTextual() ? why.asText() : ""));
            }
        }
        return picks;
    }

    /** Caracteres de control a espacio, espacios repetidos a uno y sin orillas. */
    private static String clean(String why) {
        return SPACES.matcher(CONTROL.matcher(why).replaceAll(" ")).replaceAll(" ").strip();
    }

    /** 20 a 140 caracteres, sin enlaces ni {@code @<>{}} y sin hablar del animo (RN-28). */
    static boolean isValid(String why) {
        return why.length() >= OutputSchema.WHY_MIN_CHARS
                && why.length() <= WHY_MAX_CHARS
                && !FORBIDDEN.matcher(why).find()
                && ReasonValidator.isAcceptable(why);
    }
}
