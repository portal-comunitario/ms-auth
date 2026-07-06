package com.portalcomunitario.msauth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(length = 30)
    private String telefono;

    @Column(length = 20)
    private String rut;

    @Column(length = 300)
    private String direccion;

    @Column(name = "inicio_residencia")
    private java.time.LocalDate inicioResidencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacion", length = 20)
    private EstadoValidacion estadoValidacion;

    @Column(name = "notificaciones_activas", nullable = false)
    private boolean notificacionesActivas;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Role {
        VECINO, COMMUNITY_ADMIN, PLATFORM_ADMIN
    }

    public enum EstadoValidacion {
        PENDIENTE, VALIDADO
    }

    @PrePersist
    void prePersist() {
        if (role == null) role = Role.VECINO;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (estadoValidacion == null) estadoValidacion = EstadoValidacion.PENDIENTE;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getRut() { return rut; }
    public void setRut(String rut) { this.rut = rut; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public java.time.LocalDate getInicioResidencia() { return inicioResidencia; }
    public void setInicioResidencia(java.time.LocalDate inicioResidencia) { this.inicioResidencia = inicioResidencia; }
    public EstadoValidacion getEstadoValidacion() { return estadoValidacion; }
    public void setEstadoValidacion(EstadoValidacion estadoValidacion) { this.estadoValidacion = estadoValidacion; }
    public boolean isNotificacionesActivas() { return notificacionesActivas; }
    public void setNotificacionesActivas(boolean notificacionesActivas) { this.notificacionesActivas = notificacionesActivas; }
}
