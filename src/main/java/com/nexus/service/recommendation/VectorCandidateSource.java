package com.nexus.service.recommendation;

import com.nexus.config.AgentProperties;
import com.nexus.entity.Activity;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import com.nexus.integration.qdrant.QdrantFilter;
import com.nexus.integration.qdrant.QdrantHit;
import com.nexus.repository.ActivityRepository;
import com.nexus.service.ProximityFilter;
import com.nexus.service.ProximityFilter.Coordinates;
import com.nexus.service.recommendation.agent.HardFilter;
import com.nexus.service.recommendation.agent.QueryText;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [T-06] U7 con el agente - Busca las ofertas mas parecidas a lo que la pareja quiere en Qdrant.
 *
 * <p>[t06b-agente-java] Vectoriza {@link QueryText} con el modelo de embeddings, recorre la
 * {@link RelaxationLadder} contando con el filtro duro de cada escalon ({@link HardFilter}: franja,
 * distancia y clima nunca se relajan) hasta que haya ofertas, y busca la mejor oferta de cada
 * actividad en el alias vigente. El lugar y la distancia salen del payload.
 *
 * <p>Con el agente apagado, si Ollama o Qdrant fallan, o si ningun escalon tiene ofertas, la
 * seleccion completa sale de {@link CatalogCandidateSource} (R8); si Qdrant no junta {@code n}, el
 * catalogo completa (R16).
 */
@Slf4j
@Primary
@Service
public class VectorCandidateSource implements CandidateSource {

    static final String GROUP_BY = "activity_id";

    private final ActivityRepository activities;
    private final OllamaClient ollama;
    private final QdrantClient qdrant;
    private final CatalogCandidateSource catalog;
    private final AgentProperties properties;

    public VectorCandidateSource(
            ActivityRepository activities,
            OllamaClient ollama,
            QdrantClient qdrant,
            CatalogCandidateSource catalog,
            AgentProperties properties) {
        this.activities = activities;
        this.ollama = ollama;
        this.qdrant = qdrant;
        this.catalog = catalog;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Un embed, a lo mas s conteos (s escalones, ~15) y una busqueda agrupada.
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
        float[] vector = ollama.embed(QueryText.of(query, context));
        Daypart daypart = Daypart.of(query.slotStart(), CatalogCandidateSource.CDMX);
        for (LadderStep step : RelaxationLadder.steps(query)) {
            QdrantFilter filter = HardFilter.of(step, daypart, excluded);
            if (qdrant.count(properties.alias(), filter) > 0) {
                List<QdrantHit> hits = qdrant.searchGroups(properties.alias(), vector, filter, GROUP_BY, properties.topK());
                List<Candidate> found = candidatesOf(hits, query.references());
                return topUp(found.stream().limit(n).toList(), step.relaxed(), query, context, excluded, n);
            }
        }
        return catalog.select(query, context, excluded, n);
    }

    /** Si Qdrant junto menos de {@code n}, el catalogo completa sin repetir. */
    private Selection topUp(
            List<Candidate> found,
            Set<RelaxedFilter> stepRelaxed,
            RecommendationQuery query,
            CoupleContext context,
            Set<Long> excluded,
            int n) {
        Map<Long, Candidate> chosen = new LinkedHashMap<>();
        found.forEach(c -> chosen.put(c.activity().getId(), c));
        Set<RelaxedFilter> relaxed = EnumSet.noneOf(RelaxedFilter.class);
        relaxed.addAll(stepRelaxed);
        if (chosen.size() < n) {
            Set<Long> skip = new HashSet<>(excluded);
            skip.addAll(chosen.keySet());
            Selection rest = catalog.select(query, context, skip, n - chosen.size());
            rest.candidates().forEach(c -> chosen.putIfAbsent(c.activity().getId(), c));
            relaxed.addAll(rest.relaxed());
        }
        return new Selection(new ArrayList<>(chosen.values()), relaxed, SelectionSource.VECTOR);
    }

    /**
     * Actividades de MySQL en el orden de Qdrant, una por actividad; las que ya no existen se
     * ignoran (S10).
     */
    private List<Candidate> candidatesOf(List<QdrantHit> hits, List<Coordinates> references) {
        Map<Long, QdrantHit> byActivity = new LinkedHashMap<>();
        for (QdrantHit hit : hits) {
            byActivity.putIfAbsent(longOf(hit.payload().get("activity_id")), hit);
        }
        Map<Long, Activity> found = activities.findAllById(byActivity.keySet()).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        List<Candidate> result = new ArrayList<>();
        byActivity.forEach((activityId, hit) -> {
            Activity a = found.get(activityId);
            if (a != null) {
                result.add(new Candidate(a, placeOf(hit.payload(), references), hit.score()));
            }
        });
        return result;
    }

    /** Lugar del payload con la distancia a la referencia mas lejana; {@code null} en casa. */
    @SuppressWarnings("unchecked")
    private static PlaceChoice placeOf(Map<String, Object> payload, List<Coordinates> references) {
        Object placeId = payload.get("place_id");
        if (placeId == null) {
            return null;
        }
        Map<String, Object> location = (Map<String, Object>) payload.get("location");
        Coordinates where = new Coordinates(
                ((Number) location.get("lat")).doubleValue(), ((Number) location.get("lon")).doubleValue());
        Double distanceKm = references.isEmpty()
                ? null
                : references.stream().mapToDouble(r -> ProximityFilter.distanceKm(where, r)).max().orElse(0);
        return new PlaceChoice(
                longOf(placeId), (String) payload.get("place_name"), (String) payload.get("borough"), distanceKm);
    }

    private static Long longOf(Object value) {
        return ((Number) value).longValue();
    }
}
