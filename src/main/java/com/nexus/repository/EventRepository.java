package com.nexus.repository;

import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    // Buscar eventos por link (pareja) y que no estén eliminados
    @Query("SELECT e FROM Event e " +
           "LEFT JOIN FETCH e.link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "LEFT JOIN FETCH e.creator " +
           "WHERE e.link.id = :linkId AND e.deletedAt IS NULL ORDER BY e.startDateTime ASC")
    List<Event> findByLinkIdAndNotDeleted(@Param("linkId") Long linkId);
    
    // Buscar eventos por usuario (como creador o parte del link)
    @Query("SELECT DISTINCT e FROM Event e " +
           "LEFT JOIN FETCH e.link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "LEFT JOIN FETCH e.creator " +
           "WHERE e.deletedAt IS NULL AND " +
           "(e.creator.id = :userId OR " +
           "(e.link.initiatorUser.id = :userId OR e.link.partnerUser.id = :userId)) " +
           "ORDER BY e.startDateTime ASC")
    List<Event> findByUserIdAndNotDeleted(@Param("userId") Long userId);
    
    // Buscar eventos pendientes de aprobación por usuario
    @Query("SELECT DISTINCT e FROM Event e " +
           "LEFT JOIN FETCH e.link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "LEFT JOIN FETCH e.creator " +
           "WHERE e.deletedAt IS NULL AND " +
           "e.status = 'PENDING' AND " +
           "e.link IS NOT NULL AND " +
           "e.partnerApproved = false AND " +
           "((e.link.initiatorUser.id = :userId AND e.creator.id != :userId) OR " +
           "(e.link.partnerUser.id = :userId AND e.creator.id != :userId)) " +
           "ORDER BY e.createdAt DESC")
    List<Event> findPendingApprovalByUserId(@Param("userId") Long userId);
    
    // Buscar eventos en un rango de fechas para un link específico
    @Query("SELECT e FROM Event e " +
           "LEFT JOIN FETCH e.link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "LEFT JOIN FETCH e.creator " +
           "WHERE e.link.id = :linkId AND e.deletedAt IS NULL AND " +
           "((e.startDateTime BETWEEN :startDate AND :endDate) OR " +
           "(e.endDateTime BETWEEN :startDate AND :endDate) OR " +
           "(e.startDateTime <= :startDate AND e.endDateTime >= :endDate)) " +
           "ORDER BY e.startDateTime ASC")
    List<Event> findByLinkIdAndDateRange(@Param("linkId") Long linkId, 
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);
    
    // Buscar eventos por estado
    @Query("SELECT e FROM Event e " +
           "LEFT JOIN FETCH e.link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "LEFT JOIN FETCH e.creator " +
           "WHERE e.link.id = :linkId AND e.status = :status AND e.deletedAt IS NULL " +
           "ORDER BY e.startDateTime ASC")
    List<Event> findByLinkIdAndStatus(@Param("linkId") Long linkId, @Param("status") EventStatus status);
    
    // Buscar un evento específico que pertenezca al link del usuario
    @Query("SELECT e FROM Event e " +
           "LEFT JOIN FETCH e.link l " +
           "LEFT JOIN FETCH l.initiatorUser " +
           "LEFT JOIN FETCH l.partnerUser " +
           "LEFT JOIN FETCH e.creator " +
           "WHERE e.id = :eventId AND e.deletedAt IS NULL AND " +
           "(e.link.initiatorUser.id = :userId OR e.link.partnerUser.id = :userId)")
    Optional<Event> findByIdAndUserId(@Param("eventId") Long eventId, @Param("userId") Long userId);
    
    // Contar eventos pendientes para un usuario
    @Query("SELECT COUNT(e) FROM Event e WHERE e.deletedAt IS NULL AND " +
           "e.status = 'PENDING' AND " +
           "e.partnerApproved = false AND " +
           "((e.link.initiatorUser.id = :userId AND e.creator.id != :userId) OR " +
           "(e.link.partnerUser.id = :userId AND e.creator.id != :userId))")
    long countPendingApprovalByUserId(@Param("userId") Long userId);
    
    // Buscar eventos recurrentes activos
    @Query("SELECT e FROM Event e WHERE e.link.id = :linkId AND e.isRecurring = true AND " +
           "e.status = 'CONFIRMED' AND e.deletedAt IS NULL")
    List<Event> findRecurringEventsByLinkId(@Param("linkId") Long linkId);
    
    // Actualizar status directamente con SQL nativo
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE events SET status = 'REJECTED', partner_approved = 0, partner_approved_at = NULL, updated_at = NOW() WHERE id = :eventId", nativeQuery = true)
    int updateEventStatus(@Param("eventId") Long eventId);
    
    // Buscar eventos próximos (para notificaciones)
    @Query("SELECT e FROM Event e WHERE e.status = 'CONFIRMED' AND e.deletedAt IS NULL AND " +
           "e.startDateTime BETWEEN :now AND :futureTime AND e.reminderMinutes IS NOT NULL " +
           "ORDER BY e.startDateTime ASC")
    List<Event> findUpcomingEventsForReminders(@Param("now") LocalDateTime now, 
                                              @Param("futureTime") LocalDateTime futureTime);
    
    // Buscar eventos por creador en un rango de fechas (para detectar disponibilidad)
    @Query("SELECT e FROM Event e WHERE e.creator.id = :userId AND e.deletedAt IS NULL AND " +
           "e.status != 'CANCELLED' AND " +
           "((e.startDateTime BETWEEN :startDate AND :endDate) OR " +
           "(e.endDateTime BETWEEN :startDate AND :endDate) OR " +
           "(e.startDateTime <= :startDate AND e.endDateTime >= :endDate))")
    List<Event> findByCreatorIdAndStartDateTimeBetween(@Param("userId") Long userId,
                                                         @Param("startDate") Instant startDate,
                                                         @Param("endDate") Instant endDate);
}