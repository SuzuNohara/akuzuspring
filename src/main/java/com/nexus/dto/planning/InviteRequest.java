package com.nexus.dto.planning;

import java.time.Instant;

/**
 * [libres-juntos] Invitar a planear con un horario ya elegido (desde una propuesta de «Libres
 * juntos»). Opcional: sin cuerpo se invita como siempre.
 */
public record InviteRequest(Instant slotStart, Instant slotEnd) {}
