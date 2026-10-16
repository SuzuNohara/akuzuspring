package com.nexus.model;

/** Estado de una solicitud de recomendacion. */
public enum RequestStatus {
    /** Se pueden pedir mas recomendaciones o enviar una como invitacion. */
    OPEN,
    /** Ya se envio una como invitacion (CU39); no admite otra. */
    SENT
}
