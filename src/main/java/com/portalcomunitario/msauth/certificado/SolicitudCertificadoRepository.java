package com.portalcomunitario.msauth.certificado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SolicitudCertificadoRepository extends JpaRepository<SolicitudCertificado, UUID> {
    List<SolicitudCertificado> findByVecinoEmailOrderByFechaSolicitudDesc(String vecinoEmail);
    List<SolicitudCertificado> findByEstadoOrderByFechaSolicitudAsc(EstadoCertificado estado);
    List<SolicitudCertificado> findAllByOrderByFechaSolicitudDesc();
    long countByEstado(EstadoCertificado estado);

    @Query(value = "SELECT nextval('certificado_folio_seq')", nativeQuery = true)
    long nextFolio();
}
