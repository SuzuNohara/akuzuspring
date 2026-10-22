package com.nexus.service.recommendation.agent;

import com.nexus.model.ReasonSource;
import com.nexus.service.recommendation.Candidate;
import com.nexus.service.recommendation.CatalogReasonWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * [t06b-agente-java] U12 - Ultima revision de la lista antes de guardarla (R10): cada recomendacion
 * es una candidata real (actividad y lugar), ninguna actividad se repite, el tamano es el esperado y
 * cada frase y fuente estan bien formadas. Los mensajes nombran la regla y la posicion, nunca un
 * titulo ni un id (RN-28).
 */
public final class FinalListGuard {

    private FinalListGuard() {}

    /** La lista rompio una regla. */
    public static class GuardException extends IllegalStateException {

        GuardException(String rule, int position) {
            super(rule + " en la posicion " + position);
        }
    }

    /**
     * @throws GuardException en la primera regla que no se cumple; nunca recorta
     * @implNote O(n).
     */
    public static void check(List<WrittenPick> picks, List<Candidate> pool, int expected) {
        if (picks.size() != expected) {
            throw new GuardException("tamano " + picks.size() + " distinto de " + expected, -1);
        }
        Set<String> identities = new HashSet<>();
        pool.forEach(c -> identities.add(identityOf(c)));
        Set<Long> seen = new HashSet<>();
        for (int position = 0; position < picks.size(); position++) {
            WrittenPick pick = picks.get(position);
            if (!identities.contains(identityOf(pick.candidate()))) {
                throw new GuardException("candidata desconocida", position);
            }
            if (!seen.add(pick.candidate().activity().getId())) {
                throw new GuardException("actividad repetida", position);
            }
            if (!refMatches(pick, pool)) {
                throw new GuardException("ref no coincide", position);
            }
            if (!reasonOk(pick)) {
                throw new GuardException("frase invalida", position);
            }
        }
    }

    private static String identityOf(Candidate c) {
        Long placeId = c.place() == null ? null : c.place().placeId();
        return c.activity().getId() + "@" + placeId;
    }

    /**
     * Las que eligio Mistral apuntan a su candidata (aunque su frase se haya cambiado por la del
     * catalogo); las de reglas llevan ref 0 y nunca frase del agente.
     */
    private static boolean refMatches(WrittenPick pick, List<Candidate> pool) {
        int ref = pick.ref();
        if (ref == 0) {
            return pick.source() != ReasonSource.AGENT;
        }
        return ref >= 1 && ref <= pool.size()
                && Objects.equals(pool.get(ref - 1).activity().getId(), pick.candidate().activity().getId());
    }

    private static boolean reasonOk(WrittenPick pick) {
        if (pick.source() == ReasonSource.AGENT) {
            return PickValidator.isValid(pick.reason());
        }
        return pick.source() == ReasonSource.CATALOG
                && pick.reason() != null
                && !pick.reason().isBlank()
                && pick.reason().length() <= CatalogReasonWriter.MAX_LENGTH;
    }
}
