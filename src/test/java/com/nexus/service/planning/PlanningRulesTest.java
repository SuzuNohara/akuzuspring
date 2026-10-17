package com.nexus.service.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.entity.PlanningSession;
import com.nexus.model.PlanningStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-34 / RN-40 (nuevas, 2026-10-16) - Plazos, limites de mensajes y recordatorios. */
class PlanningRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-17T18:00:00Z");

    private static PlanningSession invited(Duration left) {
        return PlanningSession.builder().status(PlanningStatus.INVITED).inviteExpiresAt(NOW.plus(left)).build();
    }

    private static PlanningSession active(Duration left) {
        return PlanningSession.builder().status(PlanningStatus.ACTIVE)
                .inviteExpiresAt(NOW).sessionExpiresAt(NOW.plus(left)).build();
    }

    @Test
    @DisplayName("la invitacion dura 24 h y la sesion 12 h desde que la pareja acepta")
    void deadlines() {
        assertThat(PlanningRules.inviteExpiry(NOW)).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(PlanningRules.sessionExpiry(NOW)).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    @DisplayName("recordatorios de la invitacion a las 12 h y a 1 h de vencer")
    void inviteReminders() {
        assertThat(PlanningRules.dueReminder(invited(Duration.ofHours(13)))
                .apply(NOW)).isEmpty();
        assertThat(PlanningRules.dueReminder(invited(Duration.ofHours(11))).apply(NOW))
                .contains(PlanningRules.Reminder.INVITE_12H);
        assertThat(PlanningRules.dueReminder(invited(Duration.ofMinutes(50))).apply(NOW))
                .contains(PlanningRules.Reminder.INVITE_1H);
    }

    @Test
    @DisplayName("recordatorios de la sesion a 6 h, 1 h y 15 min; no repite los ya enviados")
    void sessionReminders() {
        PlanningSession s = active(Duration.ofMinutes(10));
        assertThat(PlanningRules.dueReminder(s).apply(NOW)).contains(PlanningRules.Reminder.SESSION_15M);

        PlanningRules.markSent(s, PlanningRules.Reminder.SESSION_15M);
        assertThat(PlanningRules.dueReminder(s).apply(NOW)).isEmpty();
        assertThat(s.reminderSent("SESSION_6H")).isTrue();
        assertThat(s.reminderSent("SESSION_1H")).isTrue();
    }

    @Test
    @DisplayName("una sesion cerrada no recuerda nada")
    void closedSession() {
        PlanningSession s = active(Duration.ofMinutes(10));
        s.setStatus(PlanningStatus.AGREED);
        assertThat(PlanningRules.dueReminder(s).apply(NOW)).isEmpty();
    }

    @Test
    @DisplayName("20 mensajes por hora por persona y 140 por chat")
    void messageLimits() {
        assertThat(PlanningRules.messageRejection(19, 139)).isEmpty();
        assertThat(PlanningRules.messageRejection(20, 50)).isPresent();
        assertThat(PlanningRules.messageRejection(3, 140)).isPresent();
    }

    @Test
    @DisplayName("en linea si abrio la planeacion en los ultimos 30 s")
    void online() {
        assertThat(PlanningRules.isOnline(NOW.minusSeconds(20), NOW)).isTrue();
        assertThat(PlanningRules.isOnline(NOW.minusSeconds(40), NOW)).isFalse();
        assertThat(PlanningRules.isOnline(null, NOW)).isFalse();
    }

    @Test
    @DisplayName("el mensaje vacio o demasiado largo no se acepta")
    void messageBody() {
        assertThat(PlanningRules.cleanMessage("  hola  ")).isEqualTo(Optional.of("hola"));
        assertThat(PlanningRules.cleanMessage("   ")).isEmpty();
        assertThat(PlanningRules.cleanMessage("x".repeat(501))).isEmpty();
    }
}
