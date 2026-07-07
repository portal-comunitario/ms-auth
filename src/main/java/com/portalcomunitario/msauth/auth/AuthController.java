package com.portalcomunitario.msauth.auth;

import com.portalcomunitario.msauth.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${app.internal.token:portal-internal}")
    private String internalToken;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Contactos + consentimiento para ms-notifications. Uso interno: requiere token compartido. */
    @GetMapping("/contactos")
    public java.util.List<ContactoDto> contactos(@RequestParam(required = false) String emails,
                                                 @RequestParam(required = false) String tenantId,
                                                 @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (internalToken == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token interno inválido");
        }
        return authService.listContactos(emails, tenantId);
    }

    // ── Google OAuth ────────────────────────────────────────────
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@RequestBody GoogleAuthRequest request) {
        String idToken = request != null ? request.idToken() : null;
        return ResponseEntity.ok(toResponse(authService.authenticate(idToken)));
    }

    // ── Registro con correo/contraseña ──────────────────────────
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(toResponse(authService.register(request.name(), request.email(), request.password())));
    }

    // ── Login con correo/contraseña ─────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(toResponse(authService.login(request.email(), request.password())));
    }

    // ── Recuperación: solicitar enlace ──────────────────────────
    @PostMapping("/forgot")
    public ResponseEntity<Map<String, String>> forgot(@RequestBody ForgotRequest request) {
        String resetLink = authService.forgotPassword(request.email());
        Map<String, String> body = new HashMap<>();
        body.put("message", "Si el correo tiene una cuenta con contraseña, se generó un enlace de recuperación.");
        if (resetLink != null) {
            body.put("resetLink", resetLink);
        }
        return ResponseEntity.ok(body);
    }

    // ── Recuperación: aplicar nueva contraseña ──────────────────
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset(@RequestBody ResetRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada. Ya puedes iniciar sesión."));
    }

    // ── Perfil del usuario autenticado ──────────────────────────
    @GetMapping("/me")
    public ResponseEntity<ProfileDto> me(Authentication auth) {
        return ResponseEntity.ok(toProfile(authService.getProfile(auth.getName())));
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileDto> updateMe(Authentication auth, @RequestBody ProfileUpdateRequest req) {
        User updated = authService.updateProfile(
                auth.getName(), req.name(), req.telefono(), req.notificacionesActivas());
        return ResponseEntity.ok(toProfile(updated));
    }

    // ── Gestión de vecinos (solo dirigentes) ────────────────────
    @GetMapping("/vecinos")
    public List<VecinoDto> vecinos(Authentication auth) {
        requireAdmin(auth);
        return authService.listVecinos().stream().map(this::toVecino).toList();
    }

    @PutMapping("/vecinos/{id}/validar")
    public VecinoDto validar(@PathVariable UUID id, Authentication auth) {
        requireAdmin(auth);
        return toVecino(authService.setValidacion(id, User.EstadoValidacion.VALIDADO));
    }

    @PutMapping("/vecinos/{id}/revocar")
    public VecinoDto revocar(@PathVariable UUID id, Authentication auth) {
        requireAdmin(auth);
        return toVecino(authService.setValidacion(id, User.EstadoValidacion.PENDIENTE));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidToken(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Solicitud inválida";
        return ResponseEntity.status(401).body(Map.of("error", message));
    }

    private void requireAdmin(Authentication auth) {
        String role = "VECINO";
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String r = jwtAuth.getToken().getClaimAsString("role");
            if (r != null) role = r;
        }
        if (!"COMMUNITY_ADMIN".equals(role) && !"PLATFORM_ADMIN".equals(role)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Solo los dirigentes pueden gestionar vecinos");
        }
    }

    private VecinoDto toVecino(User u) {
        return new VecinoDto(
                u.getId(), u.getEmail(), u.getName(), u.getRole().name(),
                u.getEstadoValidacion() != null ? u.getEstadoValidacion().name() : "PENDIENTE",
                u.getTelefono(), u.getRut(), u.getDireccion(),
                u.getInicioResidencia() != null ? u.getInicioResidencia().toString() : null);
    }

    private AuthResponse toResponse(AuthService.AuthResult result) {
        User user = result.user();
        return new AuthResponse(
                result.token(),
                new UserDto(user.getEmail(), user.getName(), user.getRole().name(), user.getTenantId()));
    }

    private ProfileDto toProfile(User u) {
        return new ProfileDto(
                u.getEmail(), u.getName(), u.getRole().name(), u.getTenantId(),
                u.getTelefono(), u.getRut(), u.getDireccion(),
                u.getInicioResidencia() != null ? u.getInicioResidencia().toString() : null,
                u.getEstadoValidacion() != null ? u.getEstadoValidacion().name() : "PENDIENTE",
                u.isNotificacionesActivas());
    }

    public record GoogleAuthRequest(String idToken) {}
    public record RegisterRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record ForgotRequest(String email) {}
    public record ResetRequest(String token, String password) {}
    public record ProfileUpdateRequest(String name, String telefono, boolean notificacionesActivas) {}
    public record AuthResponse(String token, UserDto user) {}
    public record UserDto(String email, String name, String role, String tenantId) {}
    public record VecinoDto(UUID id, String email, String name, String role, String estadoValidacion,
                            String telefono, String rut, String direccion, String inicioResidencia) {}
    public record ProfileDto(String email, String name, String role, String tenantId,
                             String telefono, String rut, String direccion, String inicioResidencia,
                             String estadoValidacion, boolean notificacionesActivas) {}
}
