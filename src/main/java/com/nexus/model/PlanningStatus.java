package com.nexus.model;

/** RF-38 / RN-40 - Estado de una planeacion en pareja. */
public enum PlanningStatus {
    /** Invitacion enviada; la pareja tiene 24 h para unirse. */
    INVITED,
    /** Los dos estan dentro; tienen 12 h para decidir. */
    ACTIVE,
    /** Aceptaron una propuesta: hay evento aprobado por los dos. */
    AGREED,
    /** La pareja no acepto la invitacion a tiempo. */
    EXPIRED,
    /** Se acabaron las 12 h sin acuerdo. */
    CLOSED,
    /** Quien invito la cancelo antes de que la pareja se uniera. */
    CANCELLED;

    public boolean isOpen() {
        return this == INVITED || this == ACTIVE;
    }
}
