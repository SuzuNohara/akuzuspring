package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.entity.AppNotification;
import com.nexus.repository.AppNotificationRepository;
import com.nexus.service.push.PushRequested;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Cada aviso nuevo de la campanita pide su push; uno agrupado que se actualiza, no ([push-avisos] U2). */
@ExtendWith(MockitoExtension.class)
class AppNotificationServicePushTest {

    private static final Instant NOW = Instant.parse("2026-10-28T18:00:00Z");

    @Mock private AppNotificationRepository repository;
    @Mock private ApplicationEventPublisher events;
    private AppNotificationService service;

    @BeforeEach
    void setUp() {
        service = new AppNotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC), events);
    }

    @Test
    @DisplayName("should save the notice and request one push with the same texts")
    void shouldSaveTheNoticeAndRequestOnePushWithTheSameTexts() {
        service.notify(4L, 9L, "INVITE", "Ana te invita a planear una cita", "Únete para planear juntos.");

        verify(repository).save(any(AppNotification.class));
        ArgumentCaptor<PushRequested> push = ArgumentCaptor.forClass(PushRequested.class);
        verify(events).publishEvent(push.capture());
        assertThat(push.getValue()).isEqualTo(new PushRequested(
                4L, "INVITE", 9L, "Ana te invita a planear una cita", "Únete para planear juntos.", Map.of()));
    }

    @Test
    @DisplayName("should carry the extra data for the app in the push")
    void shouldCarryTheExtraDataForTheAppInThePush() {
        service.notify(4L, null, "LINK_ESTABLISHED", "¡Ya están vinculados!", "Sam se vinculó contigo.",
                Map.of("partnerName", "Sam"));

        ArgumentCaptor<PushRequested> push = ArgumentCaptor.forClass(PushRequested.class);
        verify(events).publishEvent(push.capture());
        assertThat(push.getValue().extra()).containsEntry("partnerName", "Sam");
        assertThat(push.getValue().sessionId()).isNull();
    }

    @Test
    @DisplayName("should request a push when a grouped notice is created")
    void shouldRequestAPushWhenAGroupedNoticeIsCreated() {
        when(repository.findFirstByUserIdAndSessionIdAndTypeOrderByCreatedAtDesc(4L, 9L, "MESSAGES"))
                .thenReturn(Optional.empty());

        service.notifyGrouped(4L, 9L, "MESSAGES", "Mensajes nuevos de Sam", "1 mensaje.", Duration.ofHours(1));

        verify(events).publishEvent(any(PushRequested.class));
    }

    @Test
    @DisplayName("should not request another push when a grouped notice is updated")
    void shouldNotRequestAnotherPushWhenAGroupedNoticeIsUpdated() {
        AppNotification last = AppNotification.builder().userId(4L).sessionId(9L).type("MESSAGES")
                .title("Mensajes nuevos de Sam").body("1 mensaje.").createdAt(NOW.minusSeconds(30)).build();
        when(repository.findFirstByUserIdAndSessionIdAndTypeOrderByCreatedAtDesc(4L, 9L, "MESSAGES"))
                .thenReturn(Optional.of(last));

        service.notifyGrouped(4L, 9L, "MESSAGES", "Mensajes nuevos de Sam", "2 mensajes.", Duration.ofHours(1));

        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("should clip a long title to 120 and a long body to 200 with an ellipsis")
    void shouldClipALongTitleTo120AndALongBodyTo200WithAnEllipsis() {
        String title = "T".repeat(150);
        String body = "B".repeat(250);

        service.notify(4L, null, "EVENT_PENDING", title, body);

        ArgumentCaptor<AppNotification> saved = ArgumentCaptor.forClass(AppNotification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).hasSize(120).endsWith("…");
        assertThat(saved.getValue().getBody()).hasSize(200).endsWith("…");
        ArgumentCaptor<PushRequested> push = ArgumentCaptor.forClass(PushRequested.class);
        verify(events).publishEvent(push.capture());
        assertThat(push.getValue().title()).isEqualTo(saved.getValue().getTitle());
    }

    @Test
    @DisplayName("should leave short or missing texts untouched")
    void shouldLeaveShortOrMissingTextsUntouched() {
        assertThat(AppNotificationService.clip("Evento confirmado", 120)).isEqualTo("Evento confirmado");
        assertThat(AppNotificationService.clip("x".repeat(120), 120)).hasSize(120).doesNotContain("…");
        assertThat(AppNotificationService.clip(null, 120)).isNull();
    }
}
