package com.nexus.service.recommendation.agent;

import com.nexus.entity.Activity;
import com.nexus.entity.Preference;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.OfferPlaceView;
import com.nexus.repository.PlaceRepository;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [t06b-agente-java] U1 - Arma las ofertas que se indexan: una por actividad en casa y una por cada
 * enlace actividad × lugar en la ciudad (D3). Descarta las invalidas y dice por que, como
 * {@code catalog.py} del lab.
 *
 * <p>El costo es el de la actividad aunque el lugar tenga {@code cost_override} (R9: es lo que
 * muestra la tarjeta).
 */
@Service
@RequiredArgsConstructor
public class OfferCatalog {

    private static final String HOME = "HOME";
    private static final String CITY = "CITY";
    private static final String ANY = "ANY";
    private static final String SEPARATOR = "\\|";

    private final ActivityRepository activities;
    private final PlaceRepository places;

    /** Ofertas validas y descartes, en orden de actividad y lugar. */
    public record CatalogLoad(List<Offer> offers, List<Skipped> skipped) {}

    /**
     * @param key {@code "12"} o {@code "12@40"}
     * @param reason {@code empty_title}, {@code negative_cost}, {@code bad_duration},
     *     {@code city_without_coords} o {@code link_on_home}
     */
    public record Skipped(String key, String reason) {}

    /**
     * Lee el catalogo de MySQL en dos consultas (actividades con intereses y enlaces con lugares).
     *
     * @implNote O(a + l) con a actividades y l enlaces.
     */
    @Transactional(readOnly = true)
    public CatalogLoad load() {
        Map<Long, List<OfferPlaceView>> linksByActivity = places.findAllActivityPlaces().stream()
                .collect(Collectors.groupingBy(OfferPlaceView::getActivityId, LinkedHashMap::new, Collectors.toList()));
        List<Offer> offers = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<Activity> catalog = activities.findAllWithInterests().stream()
                .sorted(Comparator.comparing(Activity::getId))
                .toList();
        for (Activity a : catalog) {
            List<OfferPlaceView> links = linksByActivity.getOrDefault(a.getId(), List.of());
            if (HOME.equals(a.getLocationScope())) {
                collect(a, null, offers, skipped);
                links.forEach(link -> skipped.add(new Skipped(keyOf(a, link), "link_on_home")));
            } else {
                links.forEach(link -> collect(a, link, offers, skipped));
            }
        }
        return new CatalogLoad(List.copyOf(offers), List.copyOf(skipped));
    }

    private static void collect(Activity a, OfferPlaceView link, List<Offer> offers, List<Skipped> skipped) {
        String reason = invalidReason(a, link);
        if (reason == null) {
            offers.add(offerOf(a, link));
        } else {
            skipped.add(new Skipped(keyOf(a, link), reason));
        }
    }

    /** Primera regla que no se cumple, o {@code null} si la oferta es valida. */
    private static String invalidReason(Activity a, OfferPlaceView link) {
        if (a.getTitle() == null || a.getTitle().isBlank()) {
            return "empty_title";
        }
        if (a.getCostMxnPp() == null || a.getCostMxnPp() < 0) {
            return "negative_cost";
        }
        if (!durationsOk(a.getDurationMin(), a.getDurationAvg(), a.getDurationMax())) {
            return "bad_duration";
        }
        if (CITY.equals(a.getLocationScope()) && (link.getLat() == null || link.getLon() == null)) {
            return "city_without_coords";
        }
        return null;
    }

    private static boolean durationsOk(Integer min, Integer avg, Integer max) {
        return min != null && avg != null && max != null && 0 < min && min <= avg && avg <= max;
    }

    private static Offer offerOf(Activity a, OfferPlaceView link) {
        Offer.OfferBuilder offer = Offer.builder()
                .activityId(a.getId())
                .title(a.getTitle())
                .description(a.getDescription())
                .rationale(a.getRationale())
                .activityType(a.getActivityType())
                .dimension(IdeaCategory.ofActivityType(a.getActivityType()))
                .interests(interestsOf(a.getInterests()))
                .ambience(ambienceOf(a.getAmbience(), link == null ? null : link.getAmbience()))
                .dayparts(splitOrAny(a.getDayparts()))
                .locationScope(a.getLocationScope())
                .priceBand(a.getPriceBand())
                .costMxnPp(a.getCostMxnPp())
                .outdoor(Boolean.TRUE.equals(a.getOutdoor()))
                .weatherOk(splitOrAny(a.getWeatherOk()))
                .durationMin(a.getDurationMin())
                .durationAvg(a.getDurationAvg())
                .durationMax(a.getDurationMax());
        if (link != null) {
            offer.placeId(link.getPlaceId())
                    .placeName(link.getName())
                    .borough(link.getBorough())
                    .lat(link.getLat())
                    .lon(link.getLon());
        }
        return offer.build();
    }

    private static List<String> interestsOf(Set<Preference> interests) {
        if (interests == null) {
            return List.of();
        }
        Collator spanish = Collator.getInstance(Locale.forLanguageTag("es"));
        return interests.stream().map(Preference::getName).filter(Objects::nonNull).sorted(spanish).toList();
    }

    /** Ambiente de la actividad y del lugar, sin repetir y en ese orden. */
    private static List<String> ambienceOf(String activity, String place) {
        Set<String> values = new LinkedHashSet<>();
        Stream.of(activity, place).forEach(text -> values.addAll(split(text)));
        return List.copyOf(values);
    }

    /** Valores separados por {@code |}; vacio o nulo se toma como {@code ANY}, igual que el catalogo de MySQL. */
    private static List<String> splitOrAny(String text) {
        List<String> values = split(text);
        return values.isEmpty() ? List.of(ANY) : values;
    }

    private static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(SEPARATOR)).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String keyOf(Activity a, OfferPlaceView link) {
        return link == null ? String.valueOf(a.getId()) : a.getId() + "@" + link.getPlaceId();
    }
}
