package com.nexus.service;

import com.nexus.dto.planning.NotificationList;
import com.nexus.dto.planning.NotificationView;
import com.nexus.entity.AppNotification;
import com.nexus.repository.AppNotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B9 - Avisos dentro de la app (invitacion, se unio, filtros, coincidencias, propuesta, acuerdo,
 * recordatorios). Mientras no haya notificaciones push, la app los muestra en Notificaciones.
 */
@Service
@RequiredArgsConstructor
public class AppNotificationService {

    private final AppNotificationRepository repository;
    private final Clock clock;

    @Transactional
    public void notify(Long userId, Long sessionId, String type, String title, String body) {
        repository.save(AppNotification.builder()
                .userId(userId)
                .sessionId(sessionId)
                .type(type)
                .title(title)
                .body(body)
                .createdAt(clock.instant())
                .build());
    }

    /**
     * Como {@link #notify}, pero si el ultimo aviso del mismo tipo en la sesion sigue sin leer y es
     * de hace menos de {@code window}, lo actualiza en lugar de crear otro ("Mensajes nuevos").
     */
    @Transactional
    public void notifyGrouped(Long userId, Long sessionId, String type, String title, String body, Duration window) {
        Instant now = clock.instant();
        repository.findFirstByUserIdAndSessionIdAndTypeOrderByCreatedAtDesc(userId, sessionId, type)
                .filter(last -> last.getReadAt() == null && last.getCreatedAt().isAfter(now.minus(window)))
                .ifPresentOrElse(last -> {
                    last.setTitle(title);
                    last.setBody(body);
                    last.setCreatedAt(now);
                    repository.save(last);
                }, () -> notify(userId, sessionId, type, title, body));
    }

    @Transactional(readOnly = true)
    public NotificationList list(Long userId) {
        List<NotificationView> items = repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getCreatedAt(),
                        n.getReadAt() != null))
                .toList();
        return new NotificationList(items, repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markAllRead(Long userId) {
        Instant now = clock.instant();
        repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .forEach(n -> n.setReadAt(now));
    }

    /** Lineas recientes de una sesion de ciertos tipos, para mostrar lo que hizo la pareja. */
    @Transactional(readOnly = true)
    public List<String> recentBodies(Long userId, Long sessionId, List<String> types) {
        return repository.findTop2ByUserIdAndSessionIdAndTypeInOrderByCreatedAtDesc(userId, sessionId, types).stream()
                .map(AppNotification::getBody)
                .toList();
    }
}
