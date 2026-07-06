package com.portalcomunitario.msauth.certificado;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Archivo asociado a una solicitud (cédula, comprobante o PDF emitido). Datos en BYTEA. */
@Entity
@Table(name = "certificado_archivos")
public class CertificadoArchivo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "solicitud_id", nullable = false)
    private UUID solicitudId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoArchivo tipo;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(length = 255)
    private String filename;

    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] data;

    public UUID getId() { return id; }
    public UUID getSolicitudId() { return solicitudId; }
    public void setSolicitudId(UUID solicitudId) { this.solicitudId = solicitudId; }
    public TipoArchivo getTipo() { return tipo; }
    public void setTipo(TipoArchivo tipo) { this.tipo = tipo; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
}
