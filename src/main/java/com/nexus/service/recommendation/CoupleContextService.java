package com.nexus.service.recommendation;

import com.nexus.entity.Link;
import com.nexus.entity.UserPreference;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.UserPreferenceRepository;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el {@link CoupleContext} de una pareja a partir de su cuestionario de preferencias. Solo
 * guarda conteos por dimension: nunca sale en la respuesta.
 */
@Service
@RequiredArgsConstructor
public class CoupleContextService {

    /** Prefijo de los nombres de {@code pref_categories} ("Bienestar Fisico"). */
    private static final String CATEGORY_PREFIX = "Bienestar ";
    /** Opcion "Ninguna de estas" de cada dimension: no es un gusto. */
    private static final String NONE_MARKER = "ninguna";

    private final UserPreferenceRepository preferences;

    /**
     * Pesos por dimension de los dos integrantes del vinculo.
     *
     * @implNote O(p) con p = preferencias de los dos.
     */
    @Transactional(readOnly = true)
    public CoupleContext build(Long userId, Link link) {
        Map<IdeaCategory, Integer> weights = new EnumMap<>(IdeaCategory.class);
        Stream.of(link.getInitiatorUser().getId(), link.getPartnerUser().getId())
                .flatMap(memberId -> preferences.findByUserId(memberId).stream())
                .filter(CoupleContextService::isRealChoice)
                .map(CoupleContextService::dimensionOf)
                .flatMap(Optional::stream)
                .forEach(category -> weights.merge(category, 1, Integer::sum));
        return new CoupleContext(weights);
    }

    private static boolean isRealChoice(UserPreference choice) {
        String name = choice.getPreference().getName();
        return name != null && !name.toLowerCase(Locale.ROOT).contains(NONE_MARKER);
    }

    private static Optional<IdeaCategory> dimensionOf(UserPreference choice) {
        String categoryName = choice.getPreference().getCategory().getName();
        String label = categoryName.startsWith(CATEGORY_PREFIX)
                ? categoryName.substring(CATEGORY_PREFIX.length())
                : categoryName;
        return Arrays.stream(IdeaCategory.values()).filter(c -> c.label().equals(label)).findFirst();
    }
}
