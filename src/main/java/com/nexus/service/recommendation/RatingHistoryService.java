package com.nexus.service.recommendation;

import com.nexus.entity.Activity;
import com.nexus.entity.DateRating;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.Link;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.DateRatingRepository;
import com.nexus.repository.EventRepository;
import com.nexus.security.AesEncryptionService;
import com.nexus.service.RatingService;
import com.nexus.service.recommendation.agent.WrittenPick;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU43 / RF-42 - Recomendacion ponderada por historial: con {@link #MIN_RATED_DATES} o mas citas
 * evaluadas (RN-35), las opciones de las dimensiones mejor calificadas por la pareja van primero.
 *
 * <p>Una cita cuenta como evaluada si al menos uno de los dos la califico (decision de la usuaria,
 * 2026-10-26). Los puntajes se descifran solo en memoria; los promedios nunca salen en una respuesta,
 * en el log ni en el prompt de Mistral (RN-32, RN-37).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RatingHistoryService {

    /** RN-35 - citas evaluadas a partir de las cuales se usa el historial. */
    static final int MIN_RATED_DATES = 3;

    private final EventRepository events;
    private final DateRatingRepository ratings;
    private final ActivityRepository activities;
    private final AesEncryptionService aes;
    private final Clock clock;

    /**
     * Citas evaluadas de la pareja y, si llegan a {@link #MIN_RATED_DATES}, su promedio por dimension.
     * Con menos no se descifra nada.
     *
     * @implNote O(e + r) con e = eventos confirmados del vinculo y r = sus calificaciones; a lo mas
     *     tres consultas (eventos, calificaciones y, si falta la categoria, actividades).
     */
    @Transactional(readOnly = true)
    public RatingHistory historyOf(Link link) {
        Instant now = clock.instant();
        Map<Long, Event> dates = events.findByLinkIdAndStatus(link.getId(), EventStatus.CONFIRMED).stream()
                .filter(e -> RatingService.isDate(e) && !e.getEndDateTime().isAfter(now))
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        if (dates.isEmpty()) {
            return RatingHistory.NONE;
        }
        Set<Long> members = Set.of(link.getInitiatorUser().getId(), link.getPartnerUser().getId());
        List<DateRating> rated = ratings.findByEventIdIn(dates.keySet()).stream()
                .filter(r -> members.contains(r.getUserId()))
                .toList();
        int ratedDates = (int) rated.stream().map(DateRating::getEventId).distinct().count();
        if (ratedDates < MIN_RATED_DATES) {
            return new RatingHistory(ratedDates, Map.of());
        }
        return new RatingHistory(ratedDates, averageByCategory(rated, dates));
    }

    /**
     * Promedio por dimension: suma de los puntajes de los dos en las citas de esa dimension entre el
     * numero de puntajes. Las citas sin dimension reconocible cuentan como evaluadas pero no promedian.
     */
    private Map<IdeaCategory, Double> averageByCategory(List<DateRating> rated, Map<Long, Event> dates) {
        Map<Long, IdeaCategory> categories = categoriesOf(dates);
        Map<IdeaCategory, int[]> sums = new EnumMap<>(IdeaCategory.class);
        for (DateRating r : rated) {
            IdeaCategory category = categories.get(r.getEventId());
            OptionalInt score = scoreOf(r);
            if (category != null && score.isPresent()) {
                int[] acc = sums.computeIfAbsent(category, c -> new int[2]);
                acc[0] += score.getAsInt();
                acc[1]++;
            }
        }
        Map<IdeaCategory, Double> averages = new EnumMap<>(IdeaCategory.class);
        sums.forEach((category, acc) -> averages.put(category, (double) acc[0] / acc[1]));
        return averages;
    }

    /** Dimension de cada cita: {@code Event.category} o, si falta, la de su actividad. */
    private Map<Long, IdeaCategory> categoriesOf(Map<Long, Event> dates) {
        Map<Long, IdeaCategory> out = new HashMap<>();
        List<Event> withoutCategory = new ArrayList<>();
        dates.values().forEach(e -> categoryOfLabel(e.getCategory())
                .ifPresentOrElse(c -> out.put(e.getId(), c), () -> withoutCategory.add(e)));
        Set<Long> activityIds = withoutCategory.stream()
                .map(Event::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (activityIds.isEmpty()) {
            return out;
        }
        Map<Long, String> typeById = activities.findAllById(activityIds).stream()
                .filter(a -> a.getActivityType() != null)
                .collect(Collectors.toMap(Activity::getId, Activity::getActivityType));
        withoutCategory.forEach(e -> {
            String type = typeById.get(e.getActivityId());
            if (type != null) {
                out.put(e.getId(), IdeaCategory.ofActivityType(type));
            }
        });
        return out;
    }

    /**
     * Dimension por su nombre ("Físico", como la guarda la invitacion) o su codigo ("FISICO"), sin
     * distinguir mayusculas ni acentos; vacia si el evento trae otra cosa o nada.
     */
    static Optional<IdeaCategory> categoryOfLabel(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String key = plain(text);
        return Arrays.stream(IdeaCategory.values())
                .filter(c -> plain(c.label()).equals(key) || plain(c.name()).equals(key))
                .findFirst();
    }

    private static String plain(String text) {
        return Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Puntaje descifrado ({@code {"score":n}}); vacio si no se pudo leer, sin registrar el dato. */
    private OptionalInt scoreOf(DateRating r) {
        try {
            String digits = aes.decrypt(r.getEncryptedScore()).replaceAll("\\D", "");
            return digits.isEmpty() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(digits));
        } catch (RuntimeException e) {
            log.warn("[t06-cu43] No se pudo leer una calificacion; se omite del historial");
            return OptionalInt.empty();
        }
    }

    /**
     * Reordena la lista final: con historial, primero las dimensiones mejor calificadas (de mayor a
     * menor promedio; empate, la que aparece antes). Dentro de cada dimension se respeta el orden del
     * compositor y las opciones de dimensiones sin calificar van al final en su orden actual. Sin
     * historial devuelve la lista tal cual (flujo en frio).
     *
     * @implNote O(n log n) con n = opciones; el orden es estable.
     */
    public static List<WrittenPick> rankByHistory(List<WrittenPick> picks, RatingHistory history) {
        if (!history.isWarm() || history.averages().isEmpty()) {
            return picks;
        }
        Map<IdeaCategory, Integer> firstSeen = new EnumMap<>(IdeaCategory.class);
        for (int i = 0; i < picks.size(); i++) {
            firstSeen.putIfAbsent(categoryOf(picks.get(i)), i);
        }
        Map<IdeaCategory, Double> averages = history.averages();
        Comparator<WrittenPick> byHistory = Comparator
                .comparing((WrittenPick p) -> averages.get(categoryOf(p)), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(p -> averages.containsKey(categoryOf(p)) ? firstSeen.get(categoryOf(p)) : 0);
        List<WrittenPick> ranked = new ArrayList<>(picks);
        ranked.sort(byHistory);
        return ranked;
    }

    private static IdeaCategory categoryOf(WrittenPick pick) {
        return IdeaCategory.ofActivityType(pick.candidate().activity().getActivityType());
    }

    /**
     * Lo que se sabe de las citas calificadas de la pareja. Nunca sale en una respuesta (RN-37).
     *
     * @param ratedDates citas con al menos una calificacion de los dos
     * @param averages promedio por dimension; vacio en el flujo en frio
     */
    public record RatingHistory(int ratedDates, Map<IdeaCategory, Double> averages) {

        static final RatingHistory NONE = new RatingHistory(0, Map.of());

        public RatingHistory {
            averages = Map.copyOf(averages);
        }

        /** RN-35 - hay historial suficiente para ordenar por el. */
        public boolean isWarm() {
            return ratedDates >= MIN_RATED_DATES;
        }
    }
}
