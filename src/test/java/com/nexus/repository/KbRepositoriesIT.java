package com.nexus.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.entity.Activity;
import com.nexus.entity.Ambience;
import com.nexus.entity.ActivityPlace;
import com.nexus.entity.BankCategory;
import com.nexus.entity.BoroughCentroid;
import com.nexus.entity.LocationScope;
import com.nexus.entity.Place;
import com.nexus.entity.TransitTime;
import com.nexus.entity.WeatherOk;
import com.nexus.harness.MySqlSchemaSupport;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * Contrato entre las entidades KB y las tablas {@code places}/{@code activities}/{@code
 * activity_places}/{@code borough_centroids}/{@code transit_times} sobre MySQL 8 real (nexus-KB-02).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Sql("/kb/kb-fixture.sql")
class KbRepositoriesIT extends MySqlSchemaSupport {

    @Autowired ActivityRepository activities;
    @Autowired PlaceRepository places;
    @Autowired ActivityPlaceRepository activityPlaces;
    @Autowired BoroughCentroidRepository centroids;
    @Autowired TransitTimeRepository transitTimes;
    @Autowired TestEntityManager em;

    @Test
    @DisplayName("should sort titles ignoring accents and case")
    void shouldSortTitlesIgnoringAccentsAndCase() {
        assertThat(activities.findAllByOrderByTitleAsc())
                .extracting(Activity::getTitle)
                .containsExactly(
                        "armar rompecabezas",
                        "Café de especialidad",
                        "Ópera en Bellas Artes",
                        "Picnic en el parque");
    }

    @Test
    @DisplayName("should filter activities by bank category ordered by title")
    void shouldFilterByBankCategory() {
        assertThat(activities.findByBankCategoryOrderByTitleAsc(BankCategory.AIRE_LIBRE))
                .extracting(Activity::getSlug)
                .containsExactly("picnic-parque");
    }

    @Test
    @DisplayName("should filter activities by location scope")
    void shouldFilterByLocationScope() {
        assertThat(activities.findByLocationScope(LocationScope.HOME))
                .extracting(Activity::getSlug)
                .containsExactly("armar-rompecabezas");
    }

    @Test
    @DisplayName("should fetch the place of an activity and count its links")
    void shouldFetchLinksWithPlace() {
        Long picnicId = activities.findBySlug("picnic-parque").orElseThrow().getId();

        List<ActivityPlace> links = activityPlaces.findWithPlaceByActivityId(picnicId);

        assertThat(links).hasSize(1);
        assertThat(links.get(0).getPlace().getSlug()).isEqualTo("parque-coyoacan");
        assertThat(activityPlaces.countByActivity_Id(picnicId)).isEqualTo(1);

        Long homeId = activities.findBySlug("armar-rompecabezas").orElseThrow().getId();
        assertThat(activityPlaces.countByActivity_Id(homeId)).isZero();
    }

    @Test
    @DisplayName("should find a borough centroid by name")
    void shouldFindBoroughCentroid() {
        Optional<BoroughCentroid> coyoacan = centroids.findByBorough("Coyoacán");
        assertThat(coyoacan).isPresent();
        assertThat(coyoacan.get().getLat()).isEqualTo(19.35);
    }

    @Test
    @DisplayName("should query places by slug, borough and bounding box")
    void shouldQueryPlaces() {
        assertThat(places.findBySlug("parque-coyoacan")).isPresent();
        assertThat(places.findByBorough("Cuauhtémoc")).hasSize(2);
        assertThat(places.findByLatBetweenAndLonBetween(19.3, 19.45, -99.2, -99.1)).hasSize(3);
    }

    @Test
    @DisplayName("should not persist changes because the entity is immutable")
    void shouldNotPersistChangesWhenEntityIsImmutable() throws Exception {
        Activity activity = activities.findBySlug("picnic-parque").orElseThrow();
        Field title = Activity.class.getDeclaredField("title");
        title.setAccessible(true);
        title.set(activity, "título alterado");

        em.flush();
        em.clear();

        assertThat(activities.findBySlug("picnic-parque").orElseThrow().getTitle())
                .isEqualTo("Picnic en el parque");
    }

    @Test
    @DisplayName("should map ambience and weather sets from the database")
    void shouldMapAmbienceAndWeatherSetsFromDatabase() {
        Place parque = places.findBySlug("parque-coyoacan").orElseThrow();
        assertThat(parque.getAmbience()).containsExactlyInAnyOrder(Ambience.NATURE, Ambience.QUIET);
        assertThat(parque.getWeatherOk()).containsExactlyInAnyOrder(WeatherOk.SUNNY, WeatherOk.CLOUDY);
    }

    @Test
    @DisplayName("should find a directed transit cell")
    void shouldFindDirectedTransitCell() {
        TransitTime there =
                transitTimes.findByOriginBoroughAndDestBorough("Coyoacán", "Cuauhtémoc").orElseThrow();
        assertThat(there.getMinutesP50()).isEqualTo(55);
        assertThat(there.getMinutesP75()).isEqualTo(70);

        TransitTime back =
                transitTimes.findByOriginBoroughAndDestBorough("Cuauhtémoc", "Coyoacán").orElseThrow();
        assertThat(back.getMinutesP50()).isEqualTo(58);
        assertThat(back.getMinutesP75()).isEqualTo(74);
    }

    @Test
    @DisplayName("should return empty for an unknown transit pair")
    void shouldReturnEmptyForUnknownTransitPair() {
        assertThat(transitTimes.findByOriginBoroughAndDestBorough("Coyoacán", "Milpa Alta"))
                .isEmpty();
    }
}
