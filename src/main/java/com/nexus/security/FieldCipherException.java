package com.nexus.security;

/**
 * Fallo al cifrar o descifrar un campo (nexus-EMO-01). Cae en el handler genérico 500 con un
 * mensaje fijo: no filtra el motivo exacto (clave, AAD, versión o manipulación), que sería un
 * oráculo para quien pruebe blobs.
 */
public class FieldCipherException extends IllegalStateException {

    public FieldCipherException(String message) {
        super(message);
    }

    public FieldCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
