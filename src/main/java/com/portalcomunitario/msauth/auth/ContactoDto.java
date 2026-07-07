package com.portalcomunitario.msauth.auth;

/**
 * Contacto + consentimiento de un vecino, para que ms-notifications resuelva
 * destinatarios de un evento (por email o por tenant).
 */
public record ContactoDto(
        String email,
        String nombre,
        String telefono,
        boolean notificacionesActivas
) {
}
