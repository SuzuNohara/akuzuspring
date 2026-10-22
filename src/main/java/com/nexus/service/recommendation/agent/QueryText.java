package com.nexus.service.recommendation.agent;

import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.service.recommendation.CoupleContext;
import com.nexus.service.recommendation.MoodLevel;
import com.nexus.service.recommendation.RecommendationQuery;
import java.text.Collator;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [t06b-agente-java] U5 - Texto de la consulta que se vectoriza: la contraparte de
 * {@link CanonicalText}, con las mismas etiquetas, para que la similitud mida afinidad y no
 * redaccion. Solo entran valores cerrados (RN-32) y el animo combinado de la pareja (RN-28).
 *
 * <p>Las etiquetas y la agrupacion de intereses tambien las usa {@link PromptRenderer}.
 */
public final class QueryText {

    /** Maximo de intereses por grupo (compartidos, tuyos, de tu pareja). */
    public static final int MAX_INTERESTS = 5;

    static final String NONE = "ninguno";

    private static final Map<Scope, String> SCOPE_LABEL = new EnumMap<>(Map.of(
            Scope.HOME, "en casa",
            Scope.CITY, "en la ciudad",
            Scope.ANY, "donde sea"));
    private static final Map<MoodLevel, String> VALENCE_LABEL = new EnumMap<>(Map.of(
            MoodLevel.BAJO, "ánimo bajo",
            MoodLevel.MEDIO, "ánimo neutral",
            MoodLevel.ALTO, "ánimo alto",
            MoodLevel.SIN_DATO, "ánimo desconocido"));
    private static final Map<MoodLevel, String> ENERGY_LABEL = new EnumMap<>(Map.of(
            MoodLevel.BAJO, "energía baja",
            MoodLevel.MEDIO, "energía media",
            MoodLevel.ALTO, "energía alta",
            MoodLevel.SIN_DATO, "energía desconocida"));

    private QueryText() {}

    /** Intereses agrupados: los de los dos, y los de cada quien sin repetir los compartidos. */
    record Interests(List<String> shared, List<String> mine, List<String> partner) {}

    /**
     * Las 4 lineas cerradas: lo que busca la pareja, intereses, animo combinado y bienestar.
     *
     * @implNote O(i log i) con i intereses.
     */
    public static String of(RecommendationQuery q, CoupleContext c) {
        Interests interests = interestsOf(c);
        return String.join(
                "\n",
                headOf(q),
                "Intereses compartidos: " + join(interests.shared()) + ". Tus intereses: " + join(interests.mine())
                        + ". Intereses de tu pareja: " + join(interests.partner()) + ".",
                "Ánimo de la pareja: " + moodLabel(c) + ".",
                "Bienestar: " + wellnessOf(c) + ".");
    }

    static Interests interestsOf(CoupleContext c) {
        Collator spanish = Collator.getInstance(Locale.forLanguageTag("es"));
        Set<String> partner = new LinkedHashSet<>(c.partner());
        List<String> shared = c.mine().stream().distinct().filter(partner::contains).sorted(spanish).toList();
        return new Interests(
                shared.stream().limit(MAX_INTERESTS).toList(),
                onlyOwn(c.mine(), shared, spanish),
                onlyOwn(c.partner(), shared, spanish));
    }

    private static List<String> onlyOwn(List<String> names, List<String> shared, Comparator<Object> order) {
        return names.stream().distinct().filter(n -> !shared.contains(n)).sorted(order).limit(MAX_INTERESTS).toList();
    }

    static String scopeLabel(Scope scope) {
        return SCOPE_LABEL.get(scope);
    }

    /** {@code ánimo bajo, energía alta}: las dos palabras del nivel combinado (RN-28). */
    static String moodLabel(CoupleContext c) {
        return VALENCE_LABEL.get(c.coupleValence()) + ", " + ENERGY_LABEL.get(c.coupleEnergy());
    }

    /** {@code Bienestar Social, Bienestar Fisico} en el orden de la enumeracion; vacio = cualquiera. */
    static String categoriesLabel(Set<IdeaCategory> categories) {
        return categories.stream().sorted().map(category -> "Bienestar " + category.label())
                .collect(Collectors.joining(", "));
    }

    static String join(List<String> items) {
        return items.isEmpty() ? NONE : String.join(", ", items);
    }

    private static String headOf(RecommendationQuery q) {
        String scope = scopeLabel(q.scope());
        return q.categories().isEmpty()
                ? "Pareja busca: " + scope + "."
                : "Pareja busca: " + categoriesLabel(q.categories()) + ". Alcance: " + scope + ".";
    }

    /** Dimensiones en las que la pareja eligio preferencias, de la que mas pesa a la que menos. */
    private static String wellnessOf(CoupleContext c) {
        List<String> labels = c.dimensionWeights().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<IdeaCategory, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> "Bienestar " + e.getKey().label())
                .toList();
        return join(labels);
    }
}
