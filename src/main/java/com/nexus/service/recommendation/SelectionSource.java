package com.nexus.service.recommendation;

/** De donde salieron las opciones: busqueda del agente (Qdrant) o catalogo de MySQL. */
public enum SelectionSource {
    VECTOR,
    CATALOG
}
