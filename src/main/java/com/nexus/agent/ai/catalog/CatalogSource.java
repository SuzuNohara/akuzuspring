package com.nexus.agent.ai.catalog;

import java.util.List;

/**
 * Fuente de entradas del catálogo a indexar (nexus-AGENT-00). El dev implementa {@code
 * KbCsvCatalogSource} (lee {@code kb/}) encima; en test se usa una fuente en memoria.
 */
public interface CatalogSource {

    List<CatalogEntry> entries();
}
