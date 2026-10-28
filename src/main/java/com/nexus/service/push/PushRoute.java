package com.nexus.service.push;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Datos que viajan con la push para que la app abra la pantalla del aviso al tocarla ([push-avisos]
 * U3). La app solo acepta estas rutas (R9).
 */
public final class PushRoute {

    static final String PLANNING = "/(planning)/session";
    static final String NOTICES = "/(events)/notifications";
    static final String HOME = "/(tabs)";
    static final String LINK = "/(tabs)/link";

    /** Los tipos de aviso de {@code PlanningService}. */
    static final Set<String> PLANNING_TYPES = Set.of(
            "INVITE", "JOINED", "CANCELLED", "FILTERS", "READY", "OPTIONS", "MATCH",
            "PROPOSAL", "DECLINED", "AGREED", "MESSAGES", "REMINDER", "EXPIRED", "CLOSED");

    private PushRoute() {}

    /** {@code {type, route, sessionId?}} mas {@code extra}; un tipo desconocido lleva a la campanita. */
    public static Map<String, String> dataFor(String type, Long sessionId, Map<String, String> extra) {
        Map<String, String> data = new LinkedHashMap<>();
        if (extra != null) {
            data.putAll(extra);
        }
        data.put("type", type);
        data.put("route", routeOf(type));
        if (sessionId != null) {
            data.put("sessionId", sessionId.toString());
        }
        return data;
    }

    private static String routeOf(String type) {
        if (PLANNING_TYPES.contains(type)) {
            return PLANNING;
        }
        if (NoticeTypes.LINK_ESTABLISHED.equals(type)) {
            return HOME;
        }
        if (NoticeTypes.LINK_DELETED.equals(type)) {
            return LINK;
        }
        return NOTICES;
    }
}
