package com.nexus.entity;

public enum EventStatus {
    PENDING("Pendiente"),
    CONFIRMED("Confirmado"),
    CANCELLED("Cancelado"),
    REJECTED("Rechazado");
    
    private final String displayName;
    
    EventStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}