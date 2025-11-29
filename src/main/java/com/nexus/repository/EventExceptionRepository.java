package com.nexus.repository;

import com.nexus.entity.EventException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventExceptionRepository extends JpaRepository<EventException, Long> {
    
    /**
     * Busca todas las excepciones para un evento específico por ID del evento
     */
    @Query("SELECT e FROM EventException e WHERE e.event.id = :eventId")
    List<EventException> findByEventId(@Param("eventId") Long eventId);
    
    /**
     * Verifica si existe una excepción para una fecha específica de un evento
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM EventException e WHERE e.event.id = :eventId AND e.exceptionDate = :exceptionDate")
    boolean existsByEventIdAndExceptionDate(@Param("eventId") Long eventId, @Param("exceptionDate") Instant exceptionDate);
    
    /**
     * Busca una excepción específica por evento y fecha
     */
    @Query("SELECT e FROM EventException e WHERE e.event.id = :eventId AND e.exceptionDate = :exceptionDate")
    EventException findByEventIdAndExceptionDate(@Param("eventId") Long eventId, @Param("exceptionDate") Instant exceptionDate);
    
    /**
     * Obtiene todas las fechas de excepción para un evento (solo los timestamps)
     */
    @Query("SELECT e.exceptionDate FROM EventException e WHERE e.event.id = :eventId")
    List<Instant> findExceptionDatesByEventId(@Param("eventId") Long eventId);
    
    /**
     * Elimina todas las excepciones de un evento
     */
    @Modifying
    @Query("DELETE FROM EventException e WHERE e.event.id = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
