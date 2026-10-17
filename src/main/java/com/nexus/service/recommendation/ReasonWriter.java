package com.nexus.service.recommendation;

import java.util.List;

/**
 * Quien escribe la frase de cada recomendacion. Hoy: {@link CatalogReasonWriter}. Al conectar el
 * agente: Mistral con limite de tiempo y respaldo del catalogo por elemento.
 */
public interface ReasonWriter {

    /** Una frase por candidata, en el mismo orden. */
    List<WrittenReason> write(List<Candidate> candidates, CoupleContext context);
}
