package com.nexus.security;

/**
 * Principal de una peticion autenticada.
 *
 * <p>Se construye SOLO con claims del token (R6 de nexus-AUTH-02): el filtro no consulta la base de
 * datos, asi que la identidad sale integra de {@code sub} y {@code email}. Su forma es contrato con
 * {@code nexus-AUTH-03}, que leera {@link #id()} en lugar del {@code {userId}} de la ruta: no se
 * cambia sin cambiar aquella actividad.
 *
 * @param id identificador del usuario, {@code long} primitivo: no existe el estado «sin id».
 * @param email correo del usuario, tomado del claim {@code email}.
 */
public record AuthenticatedUser(long id, String email) {}
