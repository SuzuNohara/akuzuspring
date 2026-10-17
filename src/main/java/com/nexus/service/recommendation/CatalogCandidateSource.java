package com.nexus.service.recommendation;

import com.nexus.entity.Activity;
import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.repository.ActivityPlaceView;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.PlaceRepository;
import com.nexus.service.ProximityFilter;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * U7 sobre MySQL - Elige actividades del catalogo recorriendo la {@link RelaxationLadder} hasta
 * juntar las pedidas. Es la seleccion mientras no esta conectado el agente y su respaldo despues.
 *
 * <p>Nunca relaja la franja ({@code duration_min} debe caber, RN-27) ni la distancia: una actividad
 * en la ciudad necesita un lugar a 15 km o menos de todas las referencias (RN-31).
 */
@Service
@RequiredArgsConstructor
public class CatalogCandidateSource implements CandidateSource {

    /** Las franjas se interpretan en hora de la CDMX. */
    static final ZoneId CDMX = ZoneId.of("America/Mexico_City");

    private static final String CITY = "CITY";
    private static final Comparator<Candidate> BEST_FIRST =
            Comparator.comparingDouble(Candidate::score)
                    .reversed()
                    .thenComparing(c -> c.activity().getTitle());

    private final ActivityRepository activities;
    private final PlaceRepository places;

    /**
     * {@inheritDoc}
     *
     * @implNote O(s * a log a) con s escalones (~15) y a actividades del catalogo (~90).
     */
    @Override
    @Transactional(readOnly = true)
    public Selection select(RecommendationQuery query, CoupleContext context, Set<Long> excluded, int n) {
        List<Activity> catalog = activities.findAll();
        Map<Long, List<ActivityPlaceView>> placesByActivity = placesOf(catalog);
        Daypart daypart = Daypart.of(query.slotStart(), CDMX);
        Map<Long, Candidate> chosen = new LinkedHashMap<>();
        Set<RelaxedFilter> relaxed = EnumSet.noneOf(RelaxedFilter.class);
        for (LadderStep step : RelaxationLadder.steps(query)) {
            if (chosen.size() >= n) {
                break;
            }
            List<Candidate> fits = fitsOf(step, daypart, catalog, placesByActivity, context, excluded, chosen);
            if (!fits.isEmpty()) {
                fits.stream().limit(n - (long) chosen.size()).forEach(c -> chosen.put(c.activity().getId(), c));
                relaxed = step.relaxed();
            }
        }
        return new Selection(new ArrayList<>(chosen.values()), relaxed, SelectionSource.CATALOG);
    }

    private List<Candidate> fitsOf(
            LadderStep step,
            Daypart daypart,
            List<Activity> catalog,
            Map<Long, List<ActivityPlaceView>> placesByActivity,
            CoupleContext context,
            Set<Long> excluded,
            Map<Long, Candidate> chosen) {
        return catalog.stream()
                .filter(a -> !excluded.contains(a.getId()) && !chosen.containsKey(a.getId()))
                .filter(a -> matches(a, step, daypart))
                .map(a -> toCandidate(a, step.query().references(), placesByActivity, context))
                .flatMap(Optional::stream)
                .sorted(BEST_FIRST)
                .toList();
    }

    private static boolean matches(Activity a, LadderStep step, Daypart daypart) {
        RecommendationQuery q = step.query();
        return (q.scope() == Scope.ANY || q.scope().name().equals(a.getLocationScope()))
                && a.getCostMxnPp() <= q.maxCostMxn()
                && q.acceptsCategory(IdeaCategory.ofActivityType(a.getActivityType()))
                && (!step.daypartRequired() || daypart.admits(a.getDayparts()))
                && a.getDurationMin() <= q.slotMinutes();
    }

    private static Optional<Candidate> toCandidate(
            Activity a,
            List<Coordinates> references,
            Map<Long, List<ActivityPlaceView>> placesByActivity,
            CoupleContext context) {
        double score = context.weight(IdeaCategory.ofActivityType(a.getActivityType()));
        if (!CITY.equals(a.getLocationScope())) {
            return Optional.of(new Candidate(a, null, score));
        }
        List<ActivityPlaceView> options = placesByActivity.getOrDefault(a.getId(), List.of());
        return nearestValidPlace(options, references).map(place -> new Candidate(a, place, score));
    }

    /** Lugar mas cercano a 15 km o menos de todas las referencias; sin referencias, el primero. */
    static Optional<PlaceChoice> nearestValidPlace(List<ActivityPlaceView> options, List<Coordinates> references) {
        if (references.isEmpty()) {
            return options.stream().findFirst().map(p -> choice(p, null));
        }
        return options.stream()
                .map(p -> choice(p, farthestKm(p, references)))
                .filter(p -> p.distanceKm() <= ProximityFilter.DEFAULT_RADIUS_KM)
                .min(Comparator.comparingDouble(PlaceChoice::distanceKm));
    }

    private static double farthestKm(ActivityPlaceView p, List<Coordinates> references) {
        Coordinates where = new Coordinates(p.getLat(), p.getLon());
        return references.stream().mapToDouble(r -> ProximityFilter.distanceKm(where, r)).max().orElse(0);
    }

    private static PlaceChoice choice(ActivityPlaceView p, Double distanceKm) {
        return new PlaceChoice(p.getPlaceId(), p.getName(), p.getBorough(), distanceKm);
    }

    private Map<Long, List<ActivityPlaceView>> placesOf(List<Activity> catalog) {
        List<Long> cityIds = catalog.stream().filter(a -> CITY.equals(a.getLocationScope())).map(Activity::getId).toList();
        if (cityIds.isEmpty()) {
            return Map.of();
        }
        return places.findByActivityIds(cityIds).stream()
                .collect(Collectors.groupingBy(ActivityPlaceView::getActivityId));
    }
}
