package com.nexus.service.recommendation;

import com.nexus.entity.Link;
import com.nexus.entity.UserPreference;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.UserPreferenceRepository;
import com.nexus.service.EmotionService;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el {@link CoupleContext} de una pareja con el cuestionario de preferencias y el animo de los
 * dos en los ultimos 7 dias. Solo guarda conteos por dimension y niveles combinados: nunca sale en
 * la respuesta.
 */
@Service
@RequiredArgsConstructor
public class CoupleContextService {

    /** Prefijo de los nombres de {@code pref_categories} ("Bienestar Fisico"). */
    private static final String CATEGORY_PREFIX = "Bienestar ";
    /** Opcion "Ninguna de estas" de cada dimension: no es un gusto. */
    private static final String NONE_MARKER = "ninguna";

    /** Dias de animo que se promedian (U6). */
    static final int MOOD_DAYS = 7;

    private final UserPreferenceRepository preferences;
    private final EmotionService emotions;

    /**
     * Pesos por dimension de los dos integrantes del vinculo y, aparte, los intereses de quien pide
     * ({@code userId}) y los de su pareja.
     *
     * @implNote O(p) con p = preferencias de los dos.
     */
    @Transactional(readOnly = true)
    public CoupleContext build(Long userId, Link link) {
        Long initiator = link.getInitiatorUser().getId();
        Long partner = link.getPartnerUser().getId();
        List<UserPreference> initiatorChoices = realChoices(initiator);
        List<UserPreference> partnerChoices = realChoices(partner);
        Map<IdeaCategory, Integer> weights = new EnumMap<>(IdeaCategory.class);
        Stream.concat(initiatorChoices.stream(), partnerChoices.stream())
                .map(CoupleContextService::dimensionOf)
                .flatMap(Optional::stream)
                .forEach(category -> weights.merge(category, 1, Integer::sum));
        List<EmotionService.MoodAverage> moods = Stream.of(initiator, partner)
                .map(memberId -> emotions.averageLastDays(memberId, MOOD_DAYS))
                .flatMap(Optional::stream)
                .toList();
        boolean askedByInitiator = initiator.equals(userId);
        return new CoupleContext(
                weights,
                MoodLevel.combine(moods.stream().map(EmotionService.MoodAverage::valence).toList()),
                MoodLevel.combine(moods.stream().map(EmotionService.MoodAverage::activation).toList()),
                namesOf(askedByInitiator ? initiatorChoices : partnerChoices),
                namesOf(askedByInitiator ? partnerChoices : initiatorChoices));
    }

    private List<UserPreference> realChoices(Long memberId) {
        return preferences.findByUserId(memberId).stream().filter(CoupleContextService::isRealChoice).toList();
    }

    private static List<String> namesOf(List<UserPreference> choices) {
        return choices.stream().map(choice -> choice.getPreference().getName()).toList();
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
