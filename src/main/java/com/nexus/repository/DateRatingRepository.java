package com.nexus.repository;

import com.nexus.entity.DateRating;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DateRatingRepository extends JpaRepository<DateRating, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    /** Eventos que esta persona ya califico, de entre los dados. */
    @Query("SELECT r.eventId FROM DateRating r WHERE r.userId = :userId AND r.eventId IN :eventIds")
    List<Long> findRatedEventIds(@Param("userId") Long userId, @Param("eventIds") Collection<Long> eventIds);

    /** [t06-cu43] Calificaciones de los dos en las citas dadas (CU43, historial de la pareja). */
    List<DateRating> findByEventIdIn(Collection<Long> eventIds);

    /** [primeros-pasos] Si alguno de los dos califico una cita desde que empezo el vinculo actual. */
    boolean existsByUserIdInAndCreatedAtGreaterThanEqual(Collection<Long> userIds, Instant since);
}
