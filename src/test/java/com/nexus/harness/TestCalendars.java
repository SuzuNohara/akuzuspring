package com.nexus.harness;

import com.nexus.entity.ExternalCalendar;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Unico sitio que conoce las columnas obligatorias de {@code calendars} para los {@code *IT}.
 *
 * <p>Usa el constructor sin argumentos para conservar los inicializadores de campo de {@code
 * ExternalCalendar} ({@code source}, {@code syncEnabled}, {@code privacyMode}, {@code isActive}):
 * el calendario queda exactamente como lo deja {@code linkCalendar}. El {@code deviceCalendarId} es
 * unico por llamada para que dos calendarios del mismo usuario nunca compartan clave de busqueda.
 */
public final class TestCalendars {
  private static final AtomicLong SEQUENCE = new AtomicLong();

  private TestCalendars() {}

  /** Persiste un calendario minimo valido (owner_user_id, name) con deviceCalendarId unico. */
  public static ExternalCalendar persist(TestEntityManager em, Long userId) {
    ExternalCalendar c = new ExternalCalendar();
    c.setUserId(userId);
    c.setCalendarName("Prueba");
    c.setDeviceCalendarId("dev-cal-" + SEQUENCE.incrementAndGet());
    return em.persistAndFlush(c);
  }
}
