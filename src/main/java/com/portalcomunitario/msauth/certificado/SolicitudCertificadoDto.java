package com.portalcomunitario.msauth.certificado;

import java.time.LocalDateTime;
import java.util.UUID;

public record SolicitudCertificadoDto(
        UUID id,
        String vecinoEmail,
        String vecinoNombre,
        String motivo,
        String rut,
        String direccion,
        String estado,
        String folio,
        String motivoRechazo,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaResolucion,
        LocalDateTime fechaVencimiento,
        boolean tienePdf
) {
    public static SolicitudCertificadoDto from(SolicitudCertificado s) {
        return new SolicitudCertificadoDto(
                s.getId(), s.getVecinoEmail(), s.getVecinoNombre(), s.getMotivo(),
                s.getRut(), s.getDireccion(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFolio(), s.getMotivoRechazo(), s.getFechaSolicitud(), s.getFechaResolucion(),
                (s.getEstado() == EstadoCertificado.EMITIDO && s.getFechaResolucion() != null)
                        ? s.getFechaResolucion().plusDays(SolicitudCertificado.VALIDEZ_DIAS) : null,
                s.getEstado() == EstadoCertificado.EMITIDO);
    }
}
