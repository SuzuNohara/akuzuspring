package com.nexus.repository;

import com.nexus.entity.AppNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
