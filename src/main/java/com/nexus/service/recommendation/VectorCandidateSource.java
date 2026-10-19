package com.nexus.service.recommendation;

import com.nexus.config.AgentProperties;
import com.nexus.entity.Activity;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantFilter;
import com.nexus.integration.qdrant.ScoredId;
import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.repository.ActivityPlaceView;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.PlaceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [T-06] U7 con el agente - Busca las actividades mas parecidas a lo que la pareja quiere en
 * Qdrant (embedding de {@code nomic-embed-text}), con los filtros duros dentro de la busqueda y la
 * misma escalera de filtros blandos del catalogo (R16). La distancia (RN-31) se revisa en Java con
 * los lugares de MySQL.
 *
 * <p>Con el agente apagado, o si Ollama o Qdrant fallan, la seleccion completa sale de
 * {@link CatalogCandidateSource} (R8); si Qdrant no junta suficientes, el catalogo completa.
 */
@Slf4j
@Primary
@Service
public class VectorCandidateSource implements CandidateSource {

    static final String QUERY_PREFIX = "search_query: ";

    /** Peso de cada preferencia de la pareja en la dimension, sobre la similitud (0 a 1). */
    private static final double PREFERENCE_BONUS = 0.01;

    private final ActivityRepository activities;
    private final PlaceRepository places;
    private final OllamaClient ollama;
    private final QdrantClient qdrant;
    private final CatalogCandidateSource catalog;
    private final AgentProperties properties;

    public VectorCandidateSource(
            ActivityRepository activities,
            PlaceRepository places,
            OllamaClient ollama,
            QdrantClient qdrant,
            CatalogCandidateSource catalog,
            AgentProperties properties) {
        this.activities = activities;
        this.places = places;
        this.ollama = ollama;
        this.qdrant = qdrant;
        this.catalog = catalog;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote O(s) busquedas en Qdrant con s escalones (~15), cada una de a lo mas
     *     {@code candidate-pool} resultados; se detiene en cuanto junta {@code n}.
     */
    @Override
    @Transactional(readOnly = true)
    public Selection select(RecommendationQuery query, CoupleContext context, Set<Long> excluded, int n) {
        if (!properties.enabled()) {
            return catalog.select(query, context, excluded, n);
        }
        try {
            return selectByVector(query, context, excluded, n);
        } catch (RuntimeException e) {
            log.warn("[T-06] Busqueda del agente no disponible, se usa el catalogo: {}", e.getMessage());
            return catalog.select(query, context, excluded, n);
        }
    }

    private Selection selectByVector(RecommendationQuery query, CoupleContext context, Set<Long> excluded, int n) {
        float[] vector = ollama.embed(queryText(query, context));
        String daypart = Daypart.of(query.slotStart(), CatalogCandidateSource.CDMX).name();
        Map<Long, Candidate> chosen = new LinkedHashMap<>();
        Set<RelaxedFilter> relaxed = EnumSet.noneOf(RelaxedFilter.class);

        for (LadderStep step : RelaxationLadder.steps(query)) {
            if (chosen.size() >= n) {
                break;
            }
            Set<Long> skip = new HashSet<>(excluded);
            skip.addAll(chosen.keySet());
            List<ScoredId> hits = qdrant.search(
                    properties.collection(), vector, filterOf(step, daypart, skip), properties.candidatePool());
            List<Candidate> fits = candidatesOf(hits, step.query(), context);
            if (!fits.isEmpty()) {
                fits.stream().limit(n - (long) chosen.size()).forEach(c -> chosen.put(c.activity().getId(), c));
                relaxed = EnumSet.noneOf(RelaxedFilter.class);
                relaxed.addAll(step.relaxed());
            }
        }

        if (chosen.size() < n) {
            Set<Long> skip = new HashSet<>(excluded);
            skip.addAll(chosen.keySet());
            Selection rest = catalog.select(query, context, skip, n - chosen.size());
            rest.candidates().forEach(c -> chosen.putIfAbsent(c.activity().getId(), c));
            relaxed.addAll(rest.relaxed());
        }
        return new Selection(new ArrayList<>(chosen.values()), relaxed, SelectionSource.VECTOR);
    }

    private static QdrantFilter filterOf(LadderStep step, String daypart, Set<Long> skip) {
        RecommendationQuery q = step.query();
        Set<String> scopes = q.scope() == Scope.ANY ? null : Set.of(q.scope().name());
        Set<String> dimensions = q.categories().stream().map(Enum::name).collect(Collectors.toSet());
        return new QdrantFilter(
                scopes,
                q.maxCostMxn(),
                dimensions,
                step.daypartRequired() ? daypart : null,
                (int) q.slotMinutes(),
                skip);
    }

    /** Actividades de MySQL en el orden de Qdrant; las que ya no existen se ignoran (S10). */
    private List<Candidate> candidatesOf(List<ScoredId> hits, RecommendationQuery query, CoupleContext context) {
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, Activity> byId = activities.findAllById(hits.stream().map(ScoredId::id).toList()).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        List<Long> cityIds = byId.values().stream()
                .filter(a -> "CITY".equals(a.getLocationScope()))
                .map(Activity::getId)
                .toList();
        Map<Long, List<ActivityPlaceView>> placesByActivity = cityIds.isEmpty()
                ? Map.of()
                : places.findByActivityIds(cityIds).stream().collect(Collectors.groupingBy(ActivityPlaceView::getActivityId));

        List<Candidate> result = new ArrayList<>();
        for (ScoredId hit : hits) {
            Activity a = byId.get(hit.id());
            if (a == null || !CatalogCandidateSource.fitsWeather(a, query)) {
                continue;
            }
            double score = hit.score() + PREFERENCE_BONUS * context.weight(IdeaCategory.ofActivityType(a.getActivityType()));
            if (!"CITY".equals(a.getLocationScope())) {
                result.add(new Candidate(a, null, score));
                continue;
            }
            Optional<PlaceChoice> place = CatalogCandidateSource.nearestValidPlace(
                    placesByActivity.getOrDefault(a.getId(), List.of()), query.references());
            place.ifPresent(p -> result.add(new Candidate(a, p, score)));
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return result;
    }

    /**
     * Lo que se busca, en palabras: dimensiones pedidas (o las que mas eligieron los dos), lugar,
     * momento del dia y el nivel combinado del animo (RN-28: nunca el de cada persona).
     */
    static String queryText(RecommendationQuery query, CoupleContext context) {
        List<String> parts = new ArrayList<>();
        parts.add("Plan para una pareja");
        List<IdeaCategory> dimensions = query.categories().isEmpty()
                ? context.dimensionWeights().entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .sorted(Map.Entry.<IdeaCategory, Integer>comparingByValue().reversed())
                        .limit(2)
                        .map(Map.Entry::getKey)
                        .toList()
                : List.copyOf(query.categories());
        if (!dimensions.isEmpty()) {
            parts.add("Bienestar " + dimensions.stream().map(IdeaCategory::label).collect(Collectors.joining(" y ")));
        }
        if (query.scope() == Scope.HOME) {
            parts.add("en casa");
        } else if (query.scope() == Scope.CITY) {
            parts.add("en la ciudad");
        }
        parts.add(switch (Daypart.of(query.slotStart(), CatalogCandidateSource.CDMX)) {
            case MORNING -> "por la mañana";
            case AFTERNOON -> "por la tarde";
            case NIGHT -> "por la noche";
        });
        if (context.coupleEnergy() == MoodLevel.BAJO || context.coupleValence() == MoodLevel.BAJO) {
            parts.add("algo tranquilo y reconfortante");
        } else if (context.coupleEnergy() == MoodLevel.ALTO) {
            parts.add("algo activo y con energía");
        }
        return QUERY_PREFIX + String.join(", ", parts) + ".";
    }
}
