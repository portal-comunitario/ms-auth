package com.portalcomunitario.msauth.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.portalcomunitario.msauth.passwordreset.PasswordResetToken;
import com.portalcomunitario.msauth.passwordreset.PasswordResetTokenRepository;
import com.portalcomunitario.msauth.user.User;
import com.portalcomunitario.msauth.user.UserRepository;
import com.portalcomunitario.msauth.messaging.Destinatario;
import com.portalcomunitario.msauth.messaging.NotificacionEvento;
import com.portalcomunitario.msauth.messaging.NotificacionPublisher;
import com.portalcomunitario.msauth.messaging.RabbitConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleIdTokenVerifier googleVerifier;
    private final SecretKey jwtKey;
    private final long jwtExpirationMillis;
    private final String frontendUrl;
    private final NotificacionPublisher notificacionPublisher;

    public AuthService(UserRepository userRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.google.client-id}") String googleClientId,
                       @Value("${app.jwt.secret}") String jwtSecret,
                       @Value("${app.jwt.expiration}") long jwtExpirationMillis,
                       @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl,
                       NotificacionPublisher notificacionPublisher) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.googleVerifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMillis = jwtExpirationMillis;
        this.frontendUrl = frontendUrl;
        this.notificacionPublisher = notificacionPublisher;
    }

    // ── Google OAuth ────────────────────────────────────────────
    public AuthResult authenticate(String googleIdToken) {
        GoogleIdToken.Payload payload = verifyGoogleToken(googleIdToken);

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) name = email;
        final String resolvedName = name;

        java.util.Optional<User> existente = userRepository.findByEmail(email);
        User user = existente.orElseGet(() -> {
            User nuevo = new User();
            nuevo.setEmail(email);
            nuevo.setName(resolvedName);
            nuevo.setRole(User.Role.VECINO);
            nuevo.setTenantId(null);
            return userRepository.save(nuevo);
        });
        if (existente.isEmpty()) {
            publicarVecinoRegistrado(user);
        }

        return new AuthResult(generateJwt(user), user);
    }

    // ── Registro con correo/contraseña ──────────────────────────
    public AuthResult register(String name, String email, String password) {
        email = normalizeEmail(email);
        if (email == null || password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Correo válido y contraseña de al menos 8 caracteres son obligatorios");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cuenta con ese correo. Inicia sesión o usa Google.");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(name != null && !name.isBlank() ? name.trim() : email);
        user.setRole(User.Role.VECINO);
        user.setTenantId(null);
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);
        publicarVecinoRegistrado(user);
        return new AuthResult(generateJwt(user), user);
    }

    // ── Login con correo/contraseña ─────────────────────────────
    public AuthResult login(String email, String password) {
        email = normalizeEmail(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"));
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Esta cuenta se creó con Google. Inicia sesión con Google.");
        }
        if (!passwordEncoder.matches(password != null ? password : "", user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        return new AuthResult(generateJwt(user), user);
    }

    // ── Recuperación: generar token (stub sin email) ────────────
    /** Devuelve el enlace de reseteo (stub — se mostrará/logueará hasta integrar el envío de correo). */
    public String forgotPassword(String email) {
        email = normalizeEmail(email);
        User user = userRepository.findByEmail(email).orElse(null);
        // Solo tiene sentido para cuentas con contraseña; si no existe, no revelamos nada.
        if (user == null || user.getPasswordHash() == null) {
            return null;
        }
        PasswordResetToken prt = new PasswordResetToken();
        prt.setEmail(email);
        prt.setToken(UUID.randomUUID().toString().replace("-", ""));
        prt.setExpiresAt(LocalDateTime.now().plusHours(1));
        prt.setUsed(false);
        resetTokenRepository.save(prt);
        String link = frontendUrl + "/reset?token=" + prt.getToken();
        // Notificación transaccional (el propio usuario la pidió): forzamos envío.
        NotificacionEvento evento = new NotificacionEvento(
                "PASSWORD_RESET",
                "Recuperación de contraseña",
                "Hola " + user.getName() + ", para restablecer tu contraseña abre este enlace (válido 1 hora): " + link,
                List.of(new Destinatario(user.getName(), user.getEmail(), user.getTelefono(), true)));
        notificacionPublisher.publicar(RabbitConfig.RK_PASSWORD_RESET, evento);
        return link;
    }

    // ── Recuperación: aplicar nueva contraseña ──────────────────
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres");
        }
        PasswordResetToken prt = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enlace de recuperación inválido"));
        if (prt.isUsed() || prt.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace de recuperación expiró o ya se usó");
        }
        User user = userRepository.findByEmail(prt.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario no encontrado"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        prt.setUsed(true);
        resetTokenRepository.save(prt);
    }

    // ── Gestión de vecinos (dirigente) ──────────────────────────
    public java.util.List<User> listVecinos() {
        return userRepository.findAll().stream()
                .sorted(java.util.Comparator
                        .comparing((User u) -> u.getEstadoValidacion() == User.EstadoValidacion.VALIDADO)
                        .thenComparing(User::getName, java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public User setValidacion(UUID id, User.EstadoValidacion estado) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vecino no encontrado"));
        u.setEstadoValidacion(estado);
        return userRepository.save(u);
    }

    public User setAcceso(UUID id, boolean aprobado) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vecino no encontrado"));
        u.setAccesoAprobado(aprobado);
        return userRepository.save(u);
    }

    /** Avisa a los dirigentes que un vecino nuevo espera aprobación de acceso. */
    private void publicarVecinoRegistrado(User nuevo) {
        java.util.List<Destinatario> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.COMMUNITY_ADMIN || u.getRole() == User.Role.PLATFORM_ADMIN)
                .map(a -> new Destinatario(a.getName(), a.getEmail(), a.getTelefono(), true))
                .toList();
        if (admins.isEmpty()) return;
        NotificacionEvento evento = new NotificacionEvento(
                "VECINO_REGISTRADO",
                "Nuevo vecino por aprobar",
                "El vecino " + nuevo.getName() + " (" + nuevo.getEmail() + ") se registró y está en revisión. "
                        + "Apruébalo en Administración › Gestión de vecinos.",
                admins);
        notificacionPublisher.publicar(RabbitConfig.RK_VECINO_REGISTRADO, evento);
    }

    public User updateVecino(UUID id, String name, String telefono, String direccion, String email) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vecino no encontrado"));
        if (name != null && !name.isBlank()) u.setName(name.trim());
        u.setTelefono(blankToNull(telefono));
        u.setDireccion(blankToNull(direccion));
        if (email != null && !email.isBlank()) {
            String norm = normalizeEmail(email);
            if (!norm.equals(u.getEmail()) && userRepository.existsByEmail(norm)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
            }
            u.setEmail(norm);
        }
        return userRepository.save(u);
    }

    public void deleteVecino(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vecino no encontrado");
        }
        userRepository.deleteById(id);
    }

    /** Contactos + consentimiento, filtrados por emails (coma) o tenantId; sin filtro = todos. */
    public List<ContactoDto> listContactos(String emails, String tenantId) {
        java.util.stream.Stream<User> stream = userRepository.findAll().stream();
        if (emails != null && !emails.isBlank()) {
            java.util.Set<String> set = java.util.Arrays.stream(emails.split(","))
                    .map(this::normalizeEmail).filter(e -> e != null && !e.isBlank())
                    .collect(java.util.stream.Collectors.toSet());
            stream = stream.filter(u -> set.contains(u.getEmail()));
        } else if (tenantId != null && !tenantId.isBlank()) {
            stream = stream.filter(u -> tenantId.equals(u.getTenantId()));
        }
        return stream.map(u -> new ContactoDto(u.getEmail(), u.getName(), u.getTelefono(), u.isNotificacionesActivas())).toList();
    }

    // ── Perfil ────────────────────────────
    public User getProfile(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    public User updateProfile(String email, String name, String telefono, boolean notificacionesActivas) {
        User user = getProfile(email);
        if (name != null && !name.isBlank()) user.setName(name.trim());
        user.setTelefono(blankToNull(telefono));
        // Solo se pueden activar notificaciones si hay teléfono registrado.
        boolean tieneTelefono = user.getTelefono() != null && !user.getTelefono().isBlank();
        user.setNotificacionesActivas(notificacionesActivas && tieneTelefono);
        // Dirección e inicio de residencia NO se editan aquí: se validan vía certificado.
        return userRepository.save(user);
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    // ── Helpers ─────────────────────────────────────────────────
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("El idToken es requerido");
        }
        try {
            GoogleIdToken idToken = googleVerifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("El idToken de Google no es válido");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("No se pudo validar el idToken de Google", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Formato de idToken inválido", e);
        }
    }

    private String generateJwt(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtExpirationMillis);
        // La comunidad (schema) contra la que se autenticó queda grabada en el token.
        // El guard rechaza el token si luego se usa contra otra comunidad.
        String schema = com.portalcomunitario.msauth.tenant.TenantContext.getCurrentTenant();
        if (schema == null || schema.isBlank()) schema = "public";
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .claim("acceso", user.isAccesoAprobado())
                .claim("tenantId", user.getTenantId())
                .claim("schema", schema)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(jwtKey)
                .compact();
    }

    public record AuthResult(String token, User user) {}
}
