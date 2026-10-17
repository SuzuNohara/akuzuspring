package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.entity.AppNotification;
import com.nexus.repository.AppNotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** B9 - Avisos dentro de la app de la planeacion en pareja. */
@ExtendWith(MockitoExtension.class)
class AppNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T18:00:00Z");

    @Mock private AppNotificationRepository repository;
    private AppNotificationService service;

    @BeforeEach
    void setUp() {
        service = new AppNotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("guarda un aviso nuevo sin leer")
    void notifyCreates() {
        service.notify(4L, 9L, "INVITE", "Ana te invita a planear una cita", "Únete para planear juntos.");

        ArgumentCaptor<AppNotification> saved = ArgumentCaptor.forClass(AppNotification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(4L);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getReadAt()).isNull();
    }

    @Test
    @DisplayName("agrupa con el ultimo del mismo tipo si sigue sin leer y es reciente")
    void groupsRecentUnread() {
        AppNotification last = AppNotification.builder().userId(4L).sessionId(9L).type("MESSAGES")
                .title("Mensajes nuevos de Sam").body("1 mensaje en su planeación.")
                .createdAt(NOW.minusSeconds(30)).build();
        when(repository.findFirstByUserIdAndSessionIdAndTypeOrderByCreatedAtDesc(4L, 9L, "MESSAGES"))
                .thenReturn(Optional.of(last));

        service.notifyGrouped(4L, 9L, "MESSAGES", "Mensajes nuevos de Sam", "2 mensajes en su planeación.",
                Duration.ofHours(1));

        assertThat(last.getBody()).isEqualTo("2 mensajes en su planeación.");
        assertThat(last.getCreatedAt()).isEqualTo(NOW);
        verify(repository).save(last);
    }

    @Test
    @DisplayName("si el ultimo ya se leyo, crea uno nuevo")
    void readOneIsNotReused() {
        AppNotification last = AppNotification.builder().userId(4L).sessionId(9L).type("MESSAGES")
                .title("t").body("b").createdAt(NOW.minusSeconds(30)).readAt(NOW.minusSeconds(10)).build();
        when(repository.findFirstByUserIdAndSessionIdAndTypeOrderByCreatedAtDesc(4L, 9L, "MESSAGES"))
                .thenReturn(Optional.of(last));

        service.notifyGrouped(4L, 9L, "MESSAGES", "Mensajes nuevos de Sam", "1 mensaje en su planeación.",
                Duration.ofHours(1));

        ArgumentCaptor<AppNotification> saved = ArgumentCaptor.forClass(AppNotification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(last);
    }

    @Test
    @DisplayName("lista los avisos y marca todos como leidos")
    void listAndRead() {
        AppNotification unread = AppNotification.builder().id(1L).userId(4L).type("MATCH")
                .title("¡Coinciden!").body("b").createdAt(NOW).build();
        when(repository.findTop50ByUserIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(unread));
        when(repository.countByUserIdAndReadAtIsNull(4L)).thenReturn(1L);

        assertThat(service.list(4L).unread()).isEqualTo(1L);
        assertThat(service.list(4L).items()).hasSize(1);

        service.markAllRead(4L);
        assertThat(unread.getReadAt()).isEqualTo(NOW);
        verify(repository, never()).delete(any());
    }
}
