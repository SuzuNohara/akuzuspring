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

    private NoticeTypes() {}
}
