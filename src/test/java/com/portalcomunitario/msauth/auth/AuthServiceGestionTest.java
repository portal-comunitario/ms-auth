package com.portalcomunitario.msauth.auth;

import com.portalcomunitario.msauth.messaging.NotificacionPublisher;
import com.portalcomunitario.msauth.passwordreset.PasswordResetToken;
import com.portalcomunitario.msauth.passwordreset.PasswordResetTokenRepository;
import com.portalcomunitario.msauth.user.User;
import com.portalcomunitario.msauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica de gestión de AuthService que NO prueba AuthServiceTest:
 * resetPassword, updateProfile, getProfile, listContactos, setValidacion,
 * setAcceso, updateVecino, deleteVecino y listVecinos.
 * NO se cubren authenticate/verifyGoogleToken (dependen del verificador de Google).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceGestionTest {

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

    private User usuario(String email, String name) {
        User u = new User();
        u.setEmail(email);
        u.setName(name);
        u.setRole(User.Role.VECINO);
        return u;
    }

    private PasswordResetToken token(String value, String email, boolean used, LocalDateTime expiresAt) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.setToken(value);
        prt.setEmail(email);
        prt.setUsed(used);
        prt.setExpiresAt(expiresAt);
        return prt;
    }

    // ---------- resetPassword ----------

    @Test
    @DisplayName("resetPassword: contraseña de menos de 8 caracteres lanza 400 y no toca el repositorio")
    void resetPassword_passwordCorta_lanza400() {
        assertThatThrownBy(() -> authService.resetPassword("tok", "corta"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("8 caracteres");
        verify(resetTokenRepository, never()).findByToken(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: token inexistente lanza 400 (enlace inválido)")
    void resetPassword_tokenInexistente_lanza400() {
        when(resetTokenRepository.findByToken("tok")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.resetPassword("tok", "password123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("inválido");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: token ya usado lanza 400")
    void resetPassword_tokenUsado_lanza400() {
        PasswordResetToken prt = token("tok", "user@example.com", true, LocalDateTime.now().plusHours(1));
        when(resetTokenRepository.findByToken("tok")).thenReturn(Optional.of(prt));
        assertThatThrownBy(() -> authService.resetPassword("tok", "password123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expiró o ya se usó");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: token expirado lanza 400")
    void resetPassword_tokenExpirado_lanza400() {
        PasswordResetToken prt = token("tok", "user@example.com", false, LocalDateTime.now().minusMinutes(1));
        when(resetTokenRepository.findByToken("tok")).thenReturn(Optional.of(prt));
        assertThatThrownBy(() -> authService.resetPassword("tok", "password123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expiró o ya se usó");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: éxito actualiza el hash, marca el token usado y guarda ambos")
    void resetPassword_exito_actualizaHashYMarcaTokenUsado() {
        PasswordResetToken prt = token("tok", "user@example.com", false, LocalDateTime.now().plusHours(1));
        User user = usuario("user@example.com", "Juan");
        user.setPasswordHash("hashViejo");
        when(resetTokenRepository.findByToken("tok")).thenReturn(Optional.of(prt));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("passwordNueva")).thenReturn("hashNuevo");

        authService.resetPassword("tok", "passwordNueva");

        assertThat(user.getPasswordHash()).isEqualTo("hashNuevo");
        assertThat(prt.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(resetTokenRepository).save(prt);
    }

    // ---------- updateProfile ----------

    @Test
    @DisplayName("updateProfile: con teléfono y notif=true deja notificacionesActivas en true")
    void updateProfile_conTelefonoYNotif_quedaActivo() {
        User user = usuario("user@example.com", "Juan");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User res = authService.updateProfile("user@example.com", "Juan Perez", "912345678", true);

        assertThat(res.getTelefono()).isEqualTo("912345678");
        assertThat(res.isNotificacionesActivas()).isTrue();
        assertThat(res.getName()).isEqualTo("Juan Perez");
    }

    @Test
    @DisplayName("updateProfile: sin teléfono y notif=true fuerza notificacionesActivas a false (regla del teléfono)")
    void updateProfile_sinTelefonoYNotif_quedaInactivo() {
        User user = usuario("user@example.com", "Juan");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User res = authService.updateProfile("user@example.com", "Juan", "   ", true);

        assertThat(res.getTelefono()).isNull();
        assertThat(res.isNotificacionesActivas()).isFalse();
    }

    // ---------- getProfile ----------

    @Test
    @DisplayName("getProfile: usuario existente se devuelve")
    void getProfile_existente_devuelve() {
        User user = usuario("user@example.com", "Juan");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThat(authService.getProfile("User@Example.com")).isSameAs(user);
    }

    @Test
    @DisplayName("getProfile: usuario inexistente lanza 404")
    void getProfile_inexistente_lanza404() {
        when(userRepository.findByEmail("no@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.getProfile("no@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
    }

    // ---------- listContactos ----------

    @Test
    @DisplayName("listContactos: filtra por la lista de emails (separados por coma)")
    void listContactos_filtraPorEmails() {
        User a = usuario("a@example.com", "Ana");
        a.setTelefono("111");
        a.setNotificacionesActivas(true);
        User b = usuario("b@example.com", "Bob");
        User c = usuario("c@example.com", "Cid");
        when(userRepository.findAll()).thenReturn(List.of(a, b, c));

        List<ContactoDto> res = authService.listContactos("A@Example.com, b@example.com", null);

        assertThat(res).extracting(ContactoDto::email)
                .containsExactlyInAnyOrder("a@example.com", "b@example.com");
        assertThat(res).extracting(ContactoDto::nombre)
                .containsExactlyInAnyOrder("Ana", "Bob");
    }

    @Test
    @DisplayName("listContactos: filtra por tenantId cuando no hay lista de emails")
    void listContactos_filtraPorTenantId() {
        User a = usuario("a@example.com", "Ana");
        a.setTenantId("t1");
        User b = usuario("b@example.com", "Bob");
        b.setTenantId("t2");
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        List<ContactoDto> res = authService.listContactos(null, "t1");

        assertThat(res).extracting(ContactoDto::email).containsExactly("a@example.com");
    }

    @Test
    @DisplayName("listContactos: sin filtros devuelve todos los contactos")
    void listContactos_sinFiltro_devuelveTodos() {
        when(userRepository.findAll()).thenReturn(List.of(
                usuario("a@example.com", "Ana"), usuario("b@example.com", "Bob")));

        assertThat(authService.listContactos(null, null)).hasSize(2);
    }

    // ---------- setValidacion ----------

    @Test
    @DisplayName("setValidacion: id inexistente lanza 404")
    void setValidacion_inexistente_lanza404() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.setValidacion(id, User.EstadoValidacion.VALIDADO))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("setValidacion: éxito guarda el nuevo estado")
    void setValidacion_exito_guarda() {
        UUID id = UUID.randomUUID();
        User user = usuario("user@example.com", "Juan");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User res = authService.setValidacion(id, User.EstadoValidacion.VALIDADO);

        assertThat(res.getEstadoValidacion()).isEqualTo(User.EstadoValidacion.VALIDADO);
        verify(userRepository).save(user);
    }

    // ---------- setAcceso ----------

    @Test
    @DisplayName("setAcceso: id inexistente lanza 404")
    void setAcceso_inexistente_lanza404() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.setAcceso(id, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("setAcceso: éxito guarda el cambio de acceso")
    void setAcceso_exito_guarda() {
        UUID id = UUID.randomUUID();
        User user = usuario("user@example.com", "Juan");
        user.setAccesoAprobado(false);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User res = authService.setAcceso(id, true);

        assertThat(res.isAccesoAprobado()).isTrue();
        verify(userRepository).save(user);
    }

    // ---------- updateVecino ----------

    @Test
    @DisplayName("updateVecino: id inexistente lanza 404")
    void updateVecino_inexistente_lanza404() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.updateVecino(id, "Nombre", null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateVecino: correo que ya existe en otro usuario lanza 409")
    void updateVecino_correoDuplicado_lanza409() {
        UUID id = UUID.randomUUID();
        User user = usuario("old@example.com", "Juan");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.updateVecino(id, "Juan", null, null, "Taken@Example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ya existe una cuenta con ese correo");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateVecino: éxito actualiza nombre, teléfono, dirección y correo normalizado")
    void updateVecino_exito_actualiza() {
        UUID id = UUID.randomUUID();
        User user = usuario("old@example.com", "Juan");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User res = authService.updateVecino(id, "  Juan Perez  ", "912345678", "Calle 1", "New@Example.com");

        assertThat(res.getName()).isEqualTo("Juan Perez");
        assertThat(res.getTelefono()).isEqualTo("912345678");
        assertThat(res.getDireccion()).isEqualTo("Calle 1");
        assertThat(res.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).save(user);
    }

    // ---------- deleteVecino ----------

    @Test
    @DisplayName("deleteVecino: id inexistente lanza 404 y no borra")
    void deleteVecino_inexistente_lanza404() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);
        assertThatThrownBy(() -> authService.deleteVecino(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteVecino: éxito llama deleteById")
    void deleteVecino_exito_borra() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        authService.deleteVecino(id);

        verify(userRepository).deleteById(id);
    }

    // ---------- listVecinos ----------

    @Test
    @DisplayName("listVecinos: ordena primero los no validados y luego por nombre (case-insensitive)")
    void listVecinos_ordenaNoValidadosPrimero() {
        User anaValidada = usuario("ana@example.com", "Ana");
        anaValidada.setEstadoValidacion(User.EstadoValidacion.VALIDADO);
        User zoePendiente = usuario("zoe@example.com", "Zoe");
        zoePendiente.setEstadoValidacion(User.EstadoValidacion.PENDIENTE);
        User bobPendiente = usuario("bob@example.com", "bob");
        bobPendiente.setEstadoValidacion(User.EstadoValidacion.PENDIENTE);
        when(userRepository.findAll()).thenReturn(List.of(anaValidada, zoePendiente, bobPendiente));

        List<User> res = authService.listVecinos();

        // Pendientes primero (por nombre): bob, Zoe; luego la validada: Ana.
        assertThat(res).extracting(User::getName).containsExactly("bob", "Zoe", "Ana");
    }
}
