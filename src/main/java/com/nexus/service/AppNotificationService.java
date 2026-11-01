package com.nexus.service;

import com.nexus.dto.planning.NotificationList;
import com.nexus.dto.planning.NotificationView;
import com.nexus.entity.AppNotification;
import com.nexus.repository.AppNotificationRepository;
import com.nexus.service.push.PushRequested;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B9 - Avisos dentro de la app (invitacion, se unio, filtros, coincidencias, propuesta, acuerdo,
 * recordatorios, eventos y vinculo). La app los muestra en Notificaciones y cada aviso nuevo sale
 * ademas como push al confirmar la transaccion ([push-avisos]).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppNotificationService {

    /** Largo de las columnas {@code title} y {@code body} de {@code app_notifications}. */
    static final int TITLE_MAX = 120;
    static final int BODY_MAX = 200;

    private final AppNotificationRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /** [ux-ajustes] D8 - Dias que se guardan los avisos ya leidos. */
    @Value("${nexus.notices.keep-days:30}")
    private int keepDays = 30;

    @Transactional
    public void notify(Long userId, Long sessionId, String type, String title, String body) {
        notify(userId, sessionId, type, title, body, Map.of());
    }

    /** Como {@link #notify}, con datos adicionales para la app (por ejemplo {@code partnerName}). */
    @Transactional
    public void notify(Long userId, Long sessionId, String type, String title, String body, Map<String, String> extra) {
        String shortTitle = clip(title, TITLE_MAX);
        String shortBody = clip(body, BODY_MAX);
        repository.save(AppNotification.builder()
                .userId(userId)
                .sessionId(sessionId)
                .type(type)
                .title(shortTitle)
                .body(shortBody)
                .createdAt(clock.instant())
                .build());
        events.publishEvent(new PushRequested(userId, type, sessionId, shortTitle, shortBody, extra));
    }

    /**
     * Como {@link #notify}, pero si el ultimo aviso del mismo tipo en la sesion sigue sin leer y es
     * de hace menos de {@code window}, lo actualiza en lugar de crear otro ("Mensajes nuevos"). Al
     * actualizar no sale otra push (R6).
     */
    @Transactional
    public void notifyGrouped(Long userId, Long sessionId, String type, String title, String body, Duration window) {
        Instant now = clock.instant();
        repository.findFirstByUserIdAndSessionIdAndTypeOrderByCreatedAtDesc(userId, sessionId, type)
                .filter(last -> last.getReadAt() == null && last.getCreatedAt().isAfter(now.minus(window)))
                .ifPresentOrElse(last -> {
                    last.setTitle(clip(title, TITLE_MAX));
                    last.setBody(clip(body, BODY_MAX));
                    last.setCreatedAt(now);
                    repository.save(last);
                }, () -> notify(userId, sessionId, type, title, body));
    }

    /** Recorta a {@code max} caracteres con "…": los titulos de los eventos pueden ser largos. */
    static String clip(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
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

    /** [ux-ajustes] D2 - El ultimo aviso de un tipo que recibio la persona desde {@code since}. */
    @Transactional(readOnly = true)
    public Optional<AppNotification> lastOf(Long userId, String type, Instant since) {
        return repository.findFirstByUserIdAndTypeAndCreatedAtAfterOrderByCreatedAtDesc(userId, type, since);
    }

    /** [ux-ajustes] D8 - "Limpiar" en la campanita: borra los avisos leidos; devuelve cuantos. */
    @Transactional
    public int clearRead(Long userId) {
        return repository.deleteReadByUserId(userId);
    }

    /** [ux-ajustes] D8 - Cada dia borra los avisos leidos de hace mas de {@code keepDays} dias. */
    @Scheduled(cron = "${nexus.notices.cleanup-cron:0 30 3 * * *}", zone = "America/Mexico_City")
    @Transactional
    public int purgeOldRead() {
        int removed = repository.deleteReadCreatedBefore(clock.instant().minus(Duration.ofDays(keepDays)));
        log.info("Avisos leidos de mas de {} dias borrados: {}", keepDays, removed);
        return removed;
    }
}
