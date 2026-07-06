package com.portalcomunitario.msauth.certificado;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "solicitudes_certificado")
public class SolicitudCertificado {

    public static final int VALIDEZ_DIAS = 45;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vecino_id", nullable = false)
    private UUID vecinoId;

    @Column(name = "vecino_email", nullable = false)
    private String vecinoEmail;

    @Column(name = "vecino_nombre")
    private String vecinoNombre;

    @Column(length = 500)
    private String motivo;

    @Column(length = 20)
    private String rut;

    @Column(length = 300)
    private String direccion;

    @Column(name = "inicio_residencia")
    private LocalDate inicioResidencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCertificado estado;

    @Column(length = 40)
    private String folio;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "fecha_solicitud", updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @PrePersist
    void onCreate() {
        if (fechaSolicitud == null) fechaSolicitud = LocalDateTime.now();
        if (estado == null) estado = EstadoCertificado.SOLICITADO;
    }

    public UUID getId() { return id; }
    public UUID getVecinoId() { return vecinoId; }
    public void setVecinoId(UUID vecinoId) { this.vecinoId = vecinoId; }
    public String getVecinoEmail() { return vecinoEmail; }
    public void setVecinoEmail(String vecinoEmail) { this.vecinoEmail = vecinoEmail; }
    public String getVecinoNombre() { return vecinoNombre; }
    public void setVecinoNombre(String vecinoNombre) { this.vecinoNombre = vecinoNombre; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getRut() { return rut; }
    public void setRut(String rut) { this.rut = rut; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public LocalDate getInicioResidencia() { return inicioResidencia; }
    public void setInicioResidencia(LocalDate inicioResidencia) { this.inicioResidencia = inicioResidencia; }
    public EstadoCertificado getEstado() { return estado; }
    public void setEstado(EstadoCertificado estado) { this.estado = estado; }
    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }
}
