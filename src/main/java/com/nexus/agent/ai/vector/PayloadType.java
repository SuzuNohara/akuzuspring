package com.nexus.agent.ai.vector;

/** Tipo de un índice de payload en el almacén vectorial (nexus-AGENT-00). */
public enum PayloadType {
    KEYWORD,
    INTEGER,
    FLOAT,
    BOOL,
    GEO;

    /** Nombre del tipo tal como lo espera Qdrant (en minúsculas). */
    public String qdrantName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
