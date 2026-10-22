package com.nexus.service.recommendation.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.entity.Activity;
import com.nexus.model.IdeaCategory;
import com.nexus.service.recommendation.Candidate;
import com.nexus.service.recommendation.CoupleContext;
import com.nexus.service.recommendation.Daypart;
import com.nexus.service.recommendation.PlaceChoice;
import com.nexus.service.recommendation.RecommendationQuery;
import com.nexus.service.recommendation.agent.QueryText.Interests;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * [t06b-agente-java] U9 - Llena las plantillas del prompt solo con valores cerrados (RN-32), igual
 * que {@code prompt.py} del lab. El render es estricto: falla si falta o sobra un valor.
 *
 * <p>Los textos del catalogo van dentro de JSON (una linea por candidata), sin caracteres de control
 * y recortados (R5): un titulo con comillas o con "ignora las instrucciones" no rompe el bloque, y el
 * esquema solo deja devolver numeros de la lista.
 */
public final class PromptRenderer {

    static final int TITLE_CHARS = 80;
    static final int DESCRIPTION_CHARS = 200;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1f\\x7f]+");
    private static final Pattern SPACES = Pattern.compile(" {2,}");
    private static final String ELLIPSIS = "…";
    private static final String NO_CATEGORY = "ninguna";
    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<Daypart, String> DAYPART_LABEL =
            new EnumMap<>(Map.of(Daypart.MORNING, "mañana", Daypart.AFTERNOON, "tarde", Daypart.NIGHT, "noche"));

    private PromptRenderer() {}

    /**
     * Sustituye cada {@code {{marcador}}} de la plantilla.
     *
     * @throws TemplateException si falta el valor de un marcador o sobra un valor
     * @implNote O(largo de la plantilla).
     */
    public static String render(String template, Map<String, String> values) {
        Set<String> declared = new TreeSet<>();
        PLACEHOLDER.matcher(template).results().forEach(m -> declared.add(m.group(1)));
        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(values.keySet());
        Set<String> unexpected = new TreeSet<>(values.keySet());
        unexpected.removeAll(declared);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new TemplateException("Plantilla con valores que faltan " + missing + " o sobran " + unexpected);
        }
        return PLACEHOLDER.matcher(template).replaceAll(m -> Matcher.quoteReplacement(values.get(m.group(1))));
    }

    /**
     * Exactamente los valores de la plantilla {@code user}: etiquetas cerradas, intereses agrupados
     * como en {@link QueryText} y el animo combinado.
     *
     * @param shown candidatas en el orden en que se numeran
     * @param k cuantas debe elegir Mistral
     */
    public static Map<String, String> values(RecommendationQuery q, CoupleContext c, List<Candidate> shown, int k) {
        Interests interests = QueryText.interestsOf(c);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("k", String.valueOf(k));
        values.put("n", String.valueOf(shown.size()));
        values.put("candidates", candidatesBlock(shown));
        values.put("prefs_shared", QueryText.join(interests.shared()));
        values.put("prefs_mine", QueryText.join(interests.mine()));
        values.put("prefs_partner", QueryText.join(interests.partner()));
        values.put("climate", QueryText.moodLabel(c));
        values.put("scope", QueryText.scopeLabel(q.scope()));
        values.put("category", q.categories().isEmpty() ? NO_CATEGORY : QueryText.categoriesLabel(q.categories()));
        values.put("budget_label", q.maxCostMxn() == 0 ? "gratis" : "hasta " + q.maxCostMxn() + " MXN por persona");
        values.put("slot_label", q.slotMinutes() + " min");
        values.put("daypart", DAYPART_LABEL.get(Daypart.of(q.slotStart(), CDMX)));
        return values;
    }

    /**
     * Una linea JSON por candidata, con {@code ref} de 1 a n.
     *
     * @implNote O(n).
     */
    public static String candidatesBlock(List<Candidate> shown) {
        return IntStream.range(0, shown.size())
                .mapToObj(i -> toJson(rowOf(i + 1, shown.get(i))))
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, Object> rowOf(int ref, Candidate candidate) {
        Activity a = candidate.activity();
        PlaceChoice place = candidate.place();
        Integer cost = a.getCostMxnPp();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ref", ref);
        row.put("titulo", clean(a.getTitle(), TITLE_CHARS));
        row.put("descripcion", clean(a.getDescription(), DESCRIPTION_CHARS));
        row.put("categoria", "Bienestar " + IdeaCategory.ofActivityType(a.getActivityType()).label());
        row.put("lugar", place == null ? "en casa" : clean(place.name() + " (" + place.borough() + ")", TITLE_CHARS));
        row.put("costo", cost == null || cost == 0 ? "gratis" : cost + " MXN por persona");
        row.put("duracion", a.getDurationAvg() + " min");
        row.put("exterior", Boolean.TRUE.equals(a.getOutdoor()));
        return row;
    }

    /** Caracteres de control a espacio, espacios repetidos a uno y corte con "…" en el limite. */
    private static String clean(String text, int limit) {
        String flat = SPACES.matcher(CONTROL.matcher(text == null ? "" : text).replaceAll(" ")).replaceAll(" ").strip();
        return flat.length() <= limit ? flat : flat.substring(0, limit - 1).stripTrailing() + ELLIPSIS;
    }

    private static String toJson(Map<String, Object> row) {
        try {
            return MAPPER.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            throw new TemplateException("No se pudo escribir la candidata como JSON", e);
        }
    }
}
