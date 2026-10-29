package com.nexus.service.push;

/**
 * Tipos de aviso de la campanita que no son de la planeacion ([push-avisos]); los de la planeacion
 * viven en {@code PlanningService}. Todos caben en los 24 caracteres de {@code app_notifications.type}.
 */
public final class NoticeTypes {

    public static final String EVENT_PENDING = "EVENT_PENDING";
    public static final String EVENT_CONFIRMED = "EVENT_CONFIRMED";
    public static final String EVENT_REJECTED = "EVENT_REJECTED";
    public static final String EVENT_DELETED = "EVENT_DELETED";
    public static final String LINK_ESTABLISHED = "LINK_ESTABLISHED";
    public static final String LINK_DELETED = "LINK_DELETED";
    /** [avisos-calendario] RF-24: cambio en el telefono un evento creado en Nexus. */
    public static final String SYNC_CONFLICT = "SYNC_CONFLICT";
    /** [avisos-calendario] RF-29: horarios libres juntos de la semana. */
    public static final String FREE_TIME = "FREE_TIME";

    private NoticeTypes() {}
}
