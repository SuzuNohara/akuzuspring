package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexus.entity.Activity;
import com.nexus.entity.Preference;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.OfferPlaceView;
import com.nexus.repository.PlaceRepository;
import com.nexus.service.recommendation.agent.OfferCatalog.CatalogLoad;
import com.nexus.service.recommendation.agent.OfferCatalog.Skipped;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [t06b-agente-java] U1 - Ofertas del catalogo: una en casa o una por actividad × lugar. */
class OfferCatalogTest {

    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final OfferCatalog catalog = new OfferCatalog(activities, places);

    static Activity.ActivityBuilder activity(long id, String scope) {
        return Activity.builder()
                .id(id)
                .title("Actividad " + id)
                .description("Descripcion " + id)
                .rationale("Razon " + id)
                .activityType("BOARD_GAME")
                .locationScope(scope)
                .dayparts("AFTERNOON|NIGHT")
                .costMxnPp(100)
                .priceBand("LOW")
                .outdoor(false)
                .weatherOk("ANY")
                .durationMin(60)
                .durationAvg(90)
                .durationMax(120)
                .ambience("SOCIAL|QUIET")
                .interests(Set.of());
    }

    static OfferPlaceView link(long activityId, long placeId, Double lat, Double lon) {
        return new OfferPlaceView() {
            public Long getActivityId() { return activityId; }
            public Long getPlaceId() { return placeId; }
            public String getName() { return "Lugar " + placeId; }
            public String getBorough() { return "Coyoacán"; }
            public Double getLat() { return lat; }
            public Double getLon() { return lon; }
            public String getAmbience() { return "QUIET|OUTDOOR"; }
            public Integer getCostOverride() { return null; }
        };
    }

    private static Preference interest(String name) {
        return Preference.builder().name(name).build();
    }

    @Test
    @DisplayName("U1.1: should give a single offer without place for an activity at home")
    void shouldGiveASingleOfferWithoutPlaceForAnActivityAtHome() {
        when(activities.findAllWithInterests()).thenReturn(List.of(activity(1, "HOME").build()));
        when(places.findAllActivityPlaces()).thenReturn(List.of());

        CatalogLoad load = catalog.load();

        assertThat(load.offers()).hasSize(1);
        Offer offer = load.offers().get(0);
        assertThat(offer.placeId()).isNull();
        assertThat(offer.key()).isEqualTo("1");
        assertThat(offer.dimension()).isEqualTo(IdeaCategory.SOCIAL);
        assertThat(offer.dayparts()).containsExactly("AFTERNOON", "NIGHT");
        assertThat(offer.ambience()).containsExactly("SOCIAL", "QUIET");
        assertThat(load.skipped()).isEmpty();
    }

    @Test
    @DisplayName("U1.2: should give one offer per place for an activity in the city")
    void shouldGiveOneOfferPerPlaceForAnActivityInTheCity() {
        when(activities.findAllWithInterests()).thenReturn(List.of(activity(2, "CITY").build()));
        when(places.findAllActivityPlaces()).thenReturn(List.of(
                link(2, 10, 19.35, -99.16), link(2, 11, 19.36, -99.17), link(2, 12, 19.37, -99.18)));

        List<Offer> offers = catalog.load().offers();

        assertThat(offers).extracting(Offer::key).containsExactly("2@10", "2@11", "2@12");
        assertThat(offers).extracting(Offer::lat).containsExactly(19.35, 19.36, 19.37);
        assertThat(offers.get(0).placeName()).isEqualTo("Lugar 10");
        assertThat(offers.get(0).borough()).isEqualTo("Coyoacán");
        assertThat(offers.get(0).ambience()).containsExactly("SOCIAL", "QUIET", "OUTDOOR");
    }

    @Test
    @DisplayName("U1.3: should skip invalid offers with their reason")
    void shouldSkipInvalidOffersWithTheirReason() {
        when(activities.findAllWithInterests()).thenReturn(List.of(
                activity(1, "HOME").title(" ").build(),
                activity(2, "HOME").costMxnPp(-5).build(),
                activity(3, "HOME").durationAvg(200).build(),
                activity(4, "CITY").build(),
                activity(5, "HOME").build()));
        when(places.findAllActivityPlaces()).thenReturn(List.of(link(4, 40, null, null), link(5, 50, 19.3, -99.1)));

        CatalogLoad load = catalog.load();

        assertThat(load.skipped()).containsExactly(
                new Skipped("1", "empty_title"),
                new Skipped("2", "negative_cost"),
                new Skipped("3", "bad_duration"),
                new Skipped("4@40", "city_without_coords"),
                new Skipped("5@50", "link_on_home"));
        assertThat(load.offers()).extracting(Offer::key).containsExactly("5");
    }

    @Test
    @DisplayName("U1.4: should take the interests as names, in alphabetical order")
    void shouldTakeTheInterestsAsNamesInAlphabeticalOrder() {
        Set<Preference> interests =
                new LinkedHashSet<>(List.of(interest("Senderismo"), interest("Cafeterías"), interest("Ajedrez")));
        when(activities.findAllWithInterests()).thenReturn(List.of(activity(1, "HOME").interests(interests).build()));
        when(places.findAllActivityPlaces()).thenReturn(List.of());

        assertThat(catalog.load().offers().get(0).interests()).containsExactly("Ajedrez", "Cafeterías", "Senderismo");
    }

    @Test
    @DisplayName("U1.5: should return empty lists for an empty catalog")
    void shouldReturnEmptyListsForAnEmptyCatalog() {
        when(activities.findAllWithInterests()).thenReturn(List.of());
        when(places.findAllActivityPlaces()).thenReturn(List.of());

        CatalogLoad load = catalog.load();

        assertThat(load.offers()).isEmpty();
        assertThat(load.skipped()).isEmpty();
    }

    @Test
    @DisplayName("U1: should accept any weather and any daypart when the catalog leaves them blank")
    void shouldAcceptAnyWeatherAndAnyDaypartWhenBlank() {
        when(activities.findAllWithInterests())
                .thenReturn(List.of(activity(1, "HOME").weatherOk("").dayparts(null).ambience("").interests(null).build()));
        when(places.findAllActivityPlaces()).thenReturn(List.of());

        Offer offer = catalog.load().offers().get(0);

        assertThat(offer.weatherOk()).containsExactly("ANY");
        assertThat(offer.dayparts()).containsExactly("ANY");
        assertThat(offer.ambience()).isEmpty();
        assertThat(offer.interests()).isEmpty();
    }
}
