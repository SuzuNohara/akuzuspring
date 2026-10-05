package com.nexus.security;

import com.nexus.exception.ForbiddenException;

/**
 * Verificaciones de pertenencia reutilizadas por los controllers (nexus-SEC-04): el usuario
 * autenticado debe coincidir con el {@code {userId}} de la URL, o ser uno de los dos participantes
 * en un recurso compartido entre pareja.
 *
 * <p>No cubre recursos cuya pertenencia depende del vinculo activo en base de datos (p. ej.
 * aprobar un evento de la pareja) -- esos los valida el propio controller/service contra
 * {@code LinkRepository}, porque no hay forma generica de resolverlo sin esa consulta.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {}

    /**
     * @throws ForbiddenException si {@code current} no es el dueno de {@code pathUserId}.
     */
    public static void requireSelf(AuthenticatedUser current, long pathUserId) {
        if (current.userId() != pathUserId) {
            throw new ForbiddenException(
                    "El usuario autenticado no coincide con el recurso solicitado.");
        }
    }

    /**
     * @throws ForbiddenException si {@code current} no es ninguno de los dos participantes.
     */
    public static void requireParticipant(AuthenticatedUser current, long userId1, long userId2) {
        if (current.userId() != userId1 && current.userId() != userId2) {
            throw new ForbiddenException(
                    "El usuario autenticado no es participante del recurso solicitado.");
        }
    }
}
