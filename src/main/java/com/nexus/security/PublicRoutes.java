package com.nexus.security;

/**
 * Las rutas publicas de la API, y ninguna mas (R5 de nexus-AUTH-02). Es una constante, no literales
 * dentro del {@code @Bean}, para que {@code PublicRoutesTest} pueda afirmar con un {@code AntPathMatcher}
 * que casa y que no casa sin levantar el contexto: la lista es donde se cometen los errores.
 *
 * <p>Las rutas se escriben sin el {@code context-path} {@code /api}: Spring Security casa contra la
 * ruta posterior al context-path.
 */
public final class PublicRoutes {

    public static final String[] PATTERNS = {
        "/auth/**", //                  registro, login, verificacion, reseteo y su health
        "/events/health", //            EventController health
        "/calendars/external/health", //ExternalCalendarController health
        "/error" //                     dispatch ERROR, filtrado por AuthorizationFilter en SS6
    };

    private PublicRoutes() {}
}
