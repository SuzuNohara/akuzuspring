package com.nexus.repository;

import com.nexus.entity.Place;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** RF-36 - Lugares posibles de cada actividad candidata, para el filtro de proximidad. */
    @Query(
            value =
                    "SELECT ap.activity_id AS activityId, p.id AS placeId, p.name AS name,"
                            + " p.borough AS borough, p.lat AS lat, p.lon AS lon"
                            + " FROM activity_places ap JOIN places p ON p.id = ap.place_id"
                            + " WHERE ap.activity_id IN (:activityIds)",
            nativeQuery = true)
    List<ActivityPlaceView> findByActivityIds(@Param("activityIds") Collection<Long> activityIds);

    /** [t06b-agente-java] U1 - Todos los enlaces actividad × lugar, para armar las ofertas que se indexan. */
    @Query(
            value =
                    "SELECT ap.activity_id AS activityId, p.id AS placeId, p.name AS name,"
                            + " p.borough AS borough, p.lat AS lat, p.lon AS lon, p.ambience AS ambience,"
                            + " ap.cost_override AS costOverride"
                            + " FROM activity_places ap JOIN places p ON p.id = ap.place_id"
                            + " ORDER BY ap.activity_id, p.id",
            nativeQuery = true)
    List<OfferPlaceView> findAllActivityPlaces();
}
