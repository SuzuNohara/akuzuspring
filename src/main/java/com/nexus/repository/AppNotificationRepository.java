package com.nexus.repository;

import com.nexus.entity.AppNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** El ultimo aviso de un tipo, para agrupar "Mensajes nuevos" mientras siga sin leer. */
    Optional<AppNotification> findFirstByUserIdAndSessionIdAndTypeOrderByCreatedAtDesc(
            Long userId, Long sessionId, String type);

    /** Lo ultimo que paso en una sesion, para las lineas "Sam cambio el presupuesto..." (B2). */
    List<AppNotification> findTop2ByUserIdAndSessionIdAndTypeInOrderByCreatedAtDesc(
            Long userId, Long sessionId, List<String> types);

    /** [ux-ajustes] D2 - El ultimo aviso de un tipo desde una fecha (el de los lunes no se repite). */
    Optional<AppNotification> findFirstByUserIdAndTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, String type, Instant since);

    /** [ux-ajustes] D8 - "Limpiar": borra los avisos leidos de una persona. */
    @Modifying
    @Query("delete from AppNotification n where n.userId = :userId and n.readAt is not null")
    int deleteReadByUserId(@Param("userId") Long userId);

    /** [ux-ajustes] D8 - Tarea diaria: borra los leidos creados antes de {@code before}. */
    @Modifying
    @Query("delete from AppNotification n where n.readAt is not null and n.createdAt < :before")
    int deleteReadCreatedBefore(@Param("before") Instant before);
}
