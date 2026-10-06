package com.nexus.repository;

import com.nexus.entity.Activity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /** Todo el catalogo, en orden alfabetico (RF-34). */
    List<Activity> findAllByOrderByTitleAsc();

    /** Actividades de ciertos {@code activity_type}, en orden alfabetico (RF-34). */
    List<Activity> findByActivityTypeInOrderByTitleAsc(Collection<String> activityTypes);
}
