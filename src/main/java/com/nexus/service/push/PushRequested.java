package com.nexus.service.push;

import java.util.Map;

/**
 * Se guardo un aviso en la campanita y hay que mandarlo como push al confirmar la transaccion
 * ([push-avisos] U2).
 *
 * @param extra datos adicionales para la app, por ejemplo {@code partnerName}; puede ser vacio
 */
public record PushRequested(
        Long userId, String type, Long sessionId, String title, String body, Map<String, String> extra) {}
