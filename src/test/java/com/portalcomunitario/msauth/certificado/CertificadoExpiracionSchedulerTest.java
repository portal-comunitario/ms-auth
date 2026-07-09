package com.portalcomunitario.msauth.certificado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificadoExpiracionSchedulerTest {

    @Mock private SolicitudCertificadoRepository solicitudRepo;
    @Mock private CertificadoArchivoRepository archivoRepo;

    private CertificadoExpiracionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CertificadoExpiracionScheduler(solicitudRepo, archivoRepo);
    }

    private SolicitudCertificado emitido(UUID id, LocalDateTime fechaResolucion) {
        SolicitudCertificado s = new SolicitudCertificado();
        try {
            Field f = SolicitudCertificado.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        s.setEstado(EstadoCertificado.EMITIDO);
        s.setFechaResolucion(fechaResolucion);
        return s;
    }

    @Test
    @DisplayName("eliminarVencidos: borra solo los certificados emitidos más antiguos que VALIDEZ_DIAS")
    void eliminarVencidos_borraSoloLosVencidos() {
        UUID idVencido = UUID.randomUUID();
        UUID idVigente = UUID.randomUUID();
        // VALIDEZ_DIAS = 45; el límite es hoy - 45 días.
        SolicitudCertificado vencido = emitido(idVencido, LocalDateTime.now().minusDays(60));
        SolicitudCertificado vigente = emitido(idVigente, LocalDateTime.now().minusDays(10));
        SolicitudCertificado sinFecha = emitido(UUID.randomUUID(), null); // fechaResolucion null -> se ignora
        when(solicitudRepo.findByEstadoOrderByFechaSolicitudAsc(EstadoCertificado.EMITIDO))
                .thenReturn(List.of(vencido, vigente, sinFecha));

        scheduler.eliminarVencidos();

        verify(archivoRepo).deleteBySolicitudId(idVencido);
        verify(solicitudRepo).delete(vencido);
        verify(archivoRepo, never()).deleteBySolicitudId(idVigente);
        verify(solicitudRepo, never()).delete(vigente);
        verify(solicitudRepo, never()).delete(sinFecha);
        // solo hubo una eliminación de archivos y una de solicitud
        verify(archivoRepo, times(1)).deleteBySolicitudId(any());
        verify(solicitudRepo, times(1)).delete(any());
    }

    @Test
    @DisplayName("eliminarVencidos: sin certificados vencidos no borra nada")
    void eliminarVencidos_ningunoVencido_noBorra() {
        SolicitudCertificado vigente = emitido(UUID.randomUUID(), LocalDateTime.now().minusDays(1));
        when(solicitudRepo.findByEstadoOrderByFechaSolicitudAsc(EstadoCertificado.EMITIDO))
                .thenReturn(List.of(vigente));

        scheduler.eliminarVencidos();

        verify(archivoRepo, never()).deleteBySolicitudId(any());
        verify(solicitudRepo, never()).delete(any());
    }
}
