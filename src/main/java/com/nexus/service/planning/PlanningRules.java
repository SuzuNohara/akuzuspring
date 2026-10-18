package com.nexus.service.planning;

import com.nexus.entity.PlanningSession;
import com.nexus.model.PlanningStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * RN-34 / RN-40 (nuevas, 2026-10-16) - Reglas puras de la planeacion en pareja. Ninguna se muestra
 * al usuario como texto (regla de interfaz de la usuaria): solo deciden.
 */
public final class PlanningRules {

    static final Duration INVITE_TTL = Duration.ofHours(24);
    static final Duration SESSION_TTL = Duration.ofHours(12);
    static final int MESSAGES_PER_HOUR = 20;
    static final int MESSAGES_PER_CHAT = 140;
    static final int MAX_MESSAGE_LENGTH = 500;
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(30);

    /** Recordatorios, del mas lejano al mas cercano a vencer dentro de cada etapa. */
    public enum Reminder {
        INVITE_12H(PlanningStatus.INVITED, Duration.ofHours(12)),
        INVITE_1H(PlanningStatus.INVITED, Duration.ofHours(1)),
        SESSION_6H(PlanningStatus.ACTIVE, Duration.ofHours(6)),
        SESSION_1H(PlanningStatus.ACTIVE, Duration.ofHours(1)),
        SESSION_15M(PlanningStatus.ACTIVE, Duration.ofMinutes(15));

        final PlanningStatus stage;
        final Duration before;

        Reminder(PlanningStatus stage, Duration before) {
            this.stage = stage;
            this.before = before;
        }
    }

    private PlanningRules() {}

    public static Instant inviteExpiry(Instant now) {
        return now.plus(INVITE_TTL);
    }

    public static Instant sessionExpiry(Instant acceptedAt) {
        return acceptedAt.plus(SESSION_TTL);
    }

    /**
     * El recordatorio mas urgente que ya toca y no se ha enviado, a evaluar en un instante dado.
     *
     * @implNote O(r) con r = 5 recordatorios.
     */
    public static Function<Instant, Optional<Reminder>> dueReminder(PlanningSession s) {
        return now -> {
            if (!s.getStatus().isOpen()) return Optional.empty();
            Instant deadline = s.getStatus() == PlanningStatus.INVITED ? s.getInviteExpiresAt() : s.getSessionExpiresAt();
            if (deadline == null || !now.isBefore(deadline)) return Optional.empty();
            Duration left = Duration.between(now, deadline);
            Reminder due = null;
            for (Reminder r : Reminder.values()) {
                if (r.stage == s.getStatus() && left.compareTo(r.before) <= 0 && !s.reminderSent(r.name())) {
                    due = r;
                }
            }
            return Optional.ofNullable(due);
        };
    }

    /** Lo que falta en palabras ("3 h 50 min", "1 hora", "15 minutos"), en minutos completos (minimo 1). */
    public static String untilPhrase(Duration left) {
        long minutes = Math.max(1, left.getSeconds() / 60);
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return minutes == 1 ? "1 minuto" : minutes + " minutos";
        }
        if (rest == 0) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        return hours + " h " + rest + " min";
    }

    /** Titulo del recordatorio de la sesion con el tiempo real ("Quedan 3 h 50 min", "Queda 1 hora"). */
    public static String leftTitle(Duration left) {
        String phrase = untilPhrase(left);
        return (phrase.startsWith("1 ") ? "Queda " : "Quedan ") + phrase;
    }

    /** Marca el recordatorio y los anteriores de su etapa, para no mandar uno viejo despues. */
    public static void markSent(PlanningSession s, Reminder sent) {
        for (Reminder r : Reminder.values()) {
            if (r.stage == sent.stage && r.ordinal() <= sent.ordinal() && !s.reminderSent(r.name())) {
                s.addReminder(r.name());
            }
        }
    }

    /** Motivo para rechazar un mensaje nuevo, o vacio si se puede enviar. */
    public static Optional<String> messageRejection(long sentLastHour, long totalInChat) {
        if (totalInChat >= MESSAGES_PER_CHAT) {
            return Optional.of("El chat de esta planeación ya está lleno.");
        }
        if (sentLastHour >= MESSAGES_PER_HOUR) {
            return Optional.of("Espera un poco antes de enviar más mensajes.");
        }
        return Optional.empty();
    }

    public static Optional<String> cleanMessage(String body) {
        if (body == null) return Optional.empty();
        String trimmed = body.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_MESSAGE_LENGTH) return Optional.empty();
        return Optional.of(trimmed);
    }

    public static boolean isOnline(Instant seenAt, Instant now) {
        return seenAt != null && !seenAt.isBefore(now.minus(ONLINE_WINDOW));
    }
}
