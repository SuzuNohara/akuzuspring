package com.nexus.security;

import com.nexus.exception.ForbiddenException;
import com.nexus.repository.LinkRepository;
import org.springframework.stereotype.Component;

/**
 * A diferencia de {@link OwnershipGuard} (pura, sin dependencias), esta guarda consulta el
 * vinculo activo en base de datos: cubre recursos que el dueno Y su pareja vinculada pueden ver
 * (p. ej. el avatar de perfil, {@code GET /profile/{userId}/avatar}), nunca modificar.
 */
@Component
public class LinkMembershipGuard {

    private final LinkRepository linkRepository;

    public LinkMembershipGuard(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    /**
     * @throws ForbiddenException si {@code current} no es {@code pathUserId} ni esta vinculado
     *     activamente con el.
     */
    public void requireSelfOrLinkedPartner(AuthenticatedUser current, long pathUserId) {
        if (current.userId() == pathUserId) {
            return;
        }
        boolean linked =
                linkRepository
                        .findActiveLinkBetweenUsers(current.userId(), pathUserId)
                        .isPresent();
        if (!linked) {
            throw new ForbiddenException(
                    "El usuario autenticado no es el dueno del recurso ni su pareja vinculada.");
        }
    }
}
