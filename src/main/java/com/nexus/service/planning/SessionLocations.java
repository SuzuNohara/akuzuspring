package com.nexus.service.planning;

import com.nexus.service.ProximityFilter.Coordinates;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * RN-30 - Ubicaciones de "Cerca de nosotros" mientras los dos tocan "Listo". Viven solo en memoria
 * y se borran al buscar o al cerrar la sesion: nunca llegan a la base de datos.
 */
@Component
public class SessionLocations {

    private final Map<Long, Map<Long, Coordinates>> bySession = new ConcurrentHashMap<>();

    public void put(Long sessionId, Long userId, Coordinates where) {
        bySession.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(userId, where);
    }

    public List<Coordinates> get(Long sessionId) {
        return List.copyOf(bySession.getOrDefault(sessionId, Map.of()).values());
    }

    public void clear(Long sessionId) {
        bySession.remove(sessionId);
    }
}
