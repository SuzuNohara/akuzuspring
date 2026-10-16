package com.nexus.model;

/** De donde salio la frase que acompana a una recomendacion. */
public enum ReasonSource {
    /** La redacto el agente (Ollama). */
    AGENT,
    /** Justificacion del catalogo: el agente fallo o excedio el tiempo (CU40 FA02). */
    CATALOG
}
