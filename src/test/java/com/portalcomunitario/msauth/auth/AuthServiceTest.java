package com.portalcomunitario.msauth.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.portalcomunitario.msauth.messaging.NotificacionPublisher;
import com.portalcomunitario.msauth.messaging.RabbitConfig;
import com.portalcomunitario.msauth.passwordreset.PasswordResetToken;
import com.portalcomunitario.msauth.passwordreset.PasswordResetTokenRepository;
import com.portalcomunitario.msauth.user.User;
import com.portalcomunitario.msauth.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String JWT_SECRET = "clave-de-prueba-hs512-con-mas-de-sesenta-y-cuatro-bytes-1234567890";

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier googleVerifier;
    @Mock private NotificacionPublisher notificacionPublisher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, resetTokenRepository, passwordEncoder,
                googleVerifier, JWT_SECRET, 3_600_000L, "http://localhost:4200", notificacionPublisher);
    }

    private User usuarioConPassword(String email, String hash) {
        User u = new User();
        u.setEmail(email);
        u.setName("Juan");
        u.setRole(User.Role.VECINO);
        u.setPasswordHash(hash);
        u.setAccesoAprobado(true);
        return u;
    }

    @Test
    @DisplayName("register: crea el vecino, hashea la contraseña y publica aviso a los admins")
    void register_correoNuevo_creaYPublica() {
        when(userRepository.existsByEmail("nuevo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setName("Admin");
        admin.setRole(User.Role.COMMUNITY_ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(admin));

        AuthService.AuthResult res = authService.register("Juan", "Nuevo@Example.com", "password123");

        assertThat(res.token()).isNotBlank();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("nuevo@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(captor.getValue().getRole()).isEqualTo(User.Role.VECINO);
        verify(notificacionPublisher).publicar(eq(RabbitConfig.RK_VECINO_REGISTRADO), any());
    }

    @Test
    @DisplayName("register: rechaza contraseñas de menos de 8 caracteres")
    void register_passwordCorta_lanzaBadRequest() {
        assertThatThrownBy(() -> authService.register("Juan", "x@example.com", "corta"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("8 caracteres");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: rechaza correo ya existente (conflicto)")
    void register_correoExistente_lanzaConflict() {
        when(userRepository.existsByEmail("x@example.com")).thenReturn(true);
        assertThatThrownBy(() -> authService.register("Juan", "x@example.com", "password123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ya existe");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: credenciales correctas devuelven un token")
    void login_exitoso_devuelveToken() {
        User u = usuarioConPassword("user@example.com", "hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        AuthService.AuthResult res = authService.login("user@example.com", "secret");

        assertThat(res.token()).isNotBlank();
        assertThat(res.user().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("login: cuenta creada con Google (sin contraseña) es rechazada")
    void login_cuentaGoogle_lanza401() {
        User u = usuarioConPassword("google@example.com", null);
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login("google@example.com", "loquesea"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Google");
    }

    @Test
    @DisplayName("login: contraseña incorrecta lanza 401")
    void login_passwordIncorrecta_lanza401() {
        User u = usuarioConPassword("user@example.com", "hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(anyString(), eq("hash"))).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@example.com", "mala"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("login: usuario inexistente lanza 401")
    void login_usuarioNoExiste_lanza401() {
        when(userRepository.findByEmail("no@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login("no@example.com", "x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("forgotPassword: cuenta de Google (sin contraseña) no genera enlace ni publica")
    void forgotPassword_cuentaGoogle_devuelveNull() {
        User u = usuarioConPassword("google@example.com", null);
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(u));

        String link = authService.forgotPassword("google@example.com");

        assertThat(link).isNull();
        verify(resetTokenRepository, never()).save(any());
        verify(notificacionPublisher, never()).publicar(anyString(), any());
    }

    @Test
    @DisplayName("forgotPassword: correo inexistente devuelve null (no revela existencia)")
    void forgotPassword_noExiste_devuelveNull() {
        when(userRepository.findByEmail("no@example.com")).thenReturn(Optional.empty());
        assertThat(authService.forgotPassword("no@example.com")).isNull();
        verify(notificacionPublisher, never()).publicar(anyString(), any());
    }

    @Test
    @DisplayName("forgotPassword: cuenta con contraseña genera enlace y publica el correo")
    void forgotPassword_conPassword_generaEnlace() {
        User u = usuarioConPassword("user@example.com", "hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));

        String link = authService.forgotPassword("user@example.com");

        assertThat(link).contains("/reset?token=");
        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        verify(notificacionPublisher).publicar(eq(RabbitConfig.RK_PASSWORD_RESET), any());
    }

    @Test
    @DisplayName("JWT: incluye el claim 'schema' (aislamiento de tenant) y el rol")
    void jwt_incluyeSchemaYRol() {
        User u = usuarioConPassword("user@example.com", "hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        String token = authService.login("user@example.com", "secret").token();

        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        assertThat(claims.get("schema")).isEqualTo("public");
        assertThat(claims.get("role")).isEqualTo("VECINO");
        assertThat(claims.get("email")).isEqualTo("user@example.com");
    }

    private GoogleIdToken googleTokenCon(String email, String name) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.set("name", name);
        com.google.api.client.json.webtoken.JsonWebSignature.Header header =
                new com.google.api.client.json.webtoken.JsonWebSignature.Header();
        return new GoogleIdToken(header, payload, new byte[0], new byte[0]);
    }

    @Test
    @DisplayName("authenticate: token de Google válido con usuario nuevo → lo crea y avisa a los admins")
    void authenticate_usuarioNuevo_creaYPublica() throws Exception {
        when(googleVerifier.verify("google-token")).thenReturn(googleTokenCon("nuevo@gmail.com", "Nuevo"));
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setName("Admin");
        admin.setRole(User.Role.COMMUNITY_ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(admin));

        AuthService.AuthResult res = authService.authenticate("google-token");

        assertThat(res.token()).isNotBlank();
        assertThat(res.user().getEmail()).isEqualTo("nuevo@gmail.com");
        verify(userRepository).save(any(User.class));
        verify(notificacionPublisher).publicar(eq(RabbitConfig.RK_VECINO_REGISTRADO), any());
    }

    @Test
    @DisplayName("authenticate: usuario existente → devuelve token sin crear ni avisar")
    void authenticate_usuarioExistente_noPublica() throws Exception {
        User existente = usuarioConPassword("ya@gmail.com", "hash");
        when(googleVerifier.verify("google-token")).thenReturn(googleTokenCon("ya@gmail.com", "Ya"));
        when(userRepository.findByEmail("ya@gmail.com")).thenReturn(Optional.of(existente));

        AuthService.AuthResult res = authService.authenticate("google-token");

        assertThat(res.token()).isNotBlank();
        verify(userRepository, never()).save(any());
        verify(notificacionPublisher, never()).publicar(anyString(), any());
    }

    @Test
    @DisplayName("authenticate: token inválido (verificador devuelve null) → lanza excepción")
    void authenticate_tokenInvalido_lanza() throws Exception {
        when(googleVerifier.verify("malo")).thenReturn(null);
        assertThatThrownBy(() -> authService.authenticate("malo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("authenticate: token vacío → lanza excepción sin llamar al verificador")
    void authenticate_tokenVacio_lanza() {
        assertThatThrownBy(() -> authService.authenticate("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
