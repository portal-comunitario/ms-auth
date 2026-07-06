package com.portalcomunitario.msauth.certificado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CertificadoArchivoRepository extends JpaRepository<CertificadoArchivo, UUID> {
    Optional<CertificadoArchivo> findBySolicitudIdAndTipo(UUID solicitudId, TipoArchivo tipo);
    void deleteBySolicitudId(UUID solicitudId);
}
