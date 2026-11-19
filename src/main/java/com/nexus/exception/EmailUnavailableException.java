package com.nexus.exception;

/**
 * [reenviar-codigo] El correo con el código no salió (p. ej. el SMTP rechazó el inicio de sesión).
 * Antes se mandaba en un hilo aparte y la app decía «Te enviamos un código nuevo» aunque fallara.
 */
public class EmailUnavailableException extends RuntimeException {

    public static final String MESSAGE =
            "No pudimos enviar el correo con tu código. Intenta de nuevo en unos minutos.";

    public EmailUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
