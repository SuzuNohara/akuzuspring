package com.nexus.repository;

import com.nexus.entity.ActivityPlace;
import com.nexus.entity.ActivityPlaceId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Vínculos actividad↔lugar (nexus-KB-02). */
@Repository
public interface ActivityPlaceRepository extends JpaRepository<ActivityPlace, ActivityPlaceId> {

    @Query(
            "SELECT ap FROM ActivityPlace ap JOIN FETCH ap.place WHERE ap.activity.id = :activityId")
    List<ActivityPlace> findWithPlaceByActivityId(@Param("activityId") Long activityId);

    long countByActivity_Id(Long activityId);
}
