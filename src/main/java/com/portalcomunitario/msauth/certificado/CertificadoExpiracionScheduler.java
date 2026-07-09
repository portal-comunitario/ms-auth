package com.portalcomunitario.msauth.certificado;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class CertificadoExpiracionScheduler {

    private final SolicitudCertificadoRepository solicitudRepo;
    private final CertificadoArchivoRepository archivoRepo;

    public CertificadoExpiracionScheduler(SolicitudCertificadoRepository solicitudRepo,
                                          CertificadoArchivoRepository archivoRepo) {
        this.solicitudRepo = solicitudRepo;
        this.archivoRepo = archivoRepo;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void eliminarVencidos() {
        LocalDateTime limite = LocalDateTime.now().minusDays(SolicitudCertificado.VALIDEZ_DIAS);
        solicitudRepo.findByEstadoOrderByFechaSolicitudAsc(EstadoCertificado.EMITIDO).stream()
                .filter(s -> s.getFechaResolucion() != null && s.getFechaResolucion().isBefore(limite))
                .forEach(s -> {
                    archivoRepo.deleteBySolicitudId(s.getId());
                    solicitudRepo.delete(s);
                });
    }
}
