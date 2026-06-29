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

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Role {
        VECINO, MODERADOR, ADMIN
    }

    @PrePersist
    void prePersist() {
        if (role == null) role = Role.VECINO;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
