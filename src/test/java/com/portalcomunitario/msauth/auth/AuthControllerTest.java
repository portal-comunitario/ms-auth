package com.portalcomunitario.msauth.auth;

import com.portalcomunitario.msauth.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba AuthController instanciándolo directamente con un AuthService mockeado
 * (sin @WebMvcTest ni contexto Spring). Verifica que cada endpoint delega en el
 * service y arma la respuesta esperada.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String INTERNAL_TOKEN = "portal-internal";

    @Mock private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
        // El campo internalToken normalmente lo inyecta @Value; aquí lo fijamos por reflexión.
        ReflectionTestUtils.setField(controller, "internalToken", INTERNAL_TOKEN);
    }

    private User usuario(String email, String name, User.Role role) {
        User u = new User();
        u.setEmail(email);
        u.setName(name);
        u.setRole(role);
        u.setTenantId("t1");
        return u;
    }

    private Authentication authConNombre(String name) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(name);
        return auth;
    }

    private JwtAuthenticationToken authConRol(String role) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("role", role).build();
        return new JwtAuthenticationToken(jwt);
    }

    // ---------- /contactos ----------

    @Test
    @DisplayName("contactos: con token interno válido delega en el service")
    void contactos_tokenValido_delegaEnService() {
        List<ContactoDto> esperado = List.of(new ContactoDto("a@example.com", "Ana", "111", true));
        when(authService.listContactos("a@example.com", "t1")).thenReturn(esperado);

        List<ContactoDto> res = controller.contactos("a@example.com", "t1", INTERNAL_TOKEN);

        assertThat(res).isSameAs(esperado);
    }

    @Test
    @DisplayName("contactos: token interno inválido lanza 401")
    void contactos_tokenInvalido_lanza401() {
        assertThatThrownBy(() -> controller.contactos(null, null, "malo"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Token interno inválido");
    }

    // ---------- /google, /register, /login ----------

    @Test
    @DisplayName("google: delega en authenticate y arma AuthResponse")
    void google_delegaEnAuthenticate() {
        User user = usuario("g@example.com", "Google User", User.Role.VECINO);
        when(authService.authenticate("id-token")).thenReturn(new AuthService.AuthResult("tok", user));

        ResponseEntity<AuthController.AuthResponse> res =
                controller.google(new AuthController.GoogleAuthRequest("id-token"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().token()).isEqualTo("tok");
        assertThat(res.getBody().user().email()).isEqualTo("g@example.com");
        assertThat(res.getBody().user().role()).isEqualTo("VECINO");
    }

    @Test
    @DisplayName("register: delega en register y arma AuthResponse")
    void register_delegaEnRegister() {
        User user = usuario("nuevo@example.com", "Nuevo", User.Role.VECINO);
        when(authService.register("Nuevo", "nuevo@example.com", "password123"))
                .thenReturn(new AuthService.AuthResult("tok", user));

        ResponseEntity<AuthController.AuthResponse> res =
                controller.register(new AuthController.RegisterRequest("Nuevo", "nuevo@example.com", "password123"));

        assertThat(res.getBody().token()).isEqualTo("tok");
        assertThat(res.getBody().user().email()).isEqualTo("nuevo@example.com");
    }

    @Test
    @DisplayName("login: delega en login y arma AuthResponse")
    void login_delegaEnLogin() {
        User user = usuario("user@example.com", "Juan", User.Role.VECINO);
        when(authService.login("user@example.com", "secret"))
                .thenReturn(new AuthService.AuthResult("tok", user));

        ResponseEntity<AuthController.AuthResponse> res =
                controller.login(new AuthController.LoginRequest("user@example.com", "secret"));

        assertThat(res.getBody().token()).isEqualTo("tok");
        assertThat(res.getBody().user().name()).isEqualTo("Juan");
    }

    // ---------- /forgot ----------

    @Test
    @DisplayName("forgot: si hay enlace lo incluye junto al mensaje")
    void forgot_conEnlace_incluyeResetLink() {
        when(authService.forgotPassword("user@example.com"))
                .thenReturn("http://localhost:4200/reset?token=abc");

        ResponseEntity<Map<String, String>> res =
                controller.forgot(new AuthController.ForgotRequest("user@example.com"));

        assertThat(res.getBody()).containsEntry("resetLink", "http://localhost:4200/reset?token=abc");
        assertThat(res.getBody()).containsKey("message");
    }

    @Test
    @DisplayName("forgot: sin enlace devuelve solo el mensaje (no revela existencia)")
    void forgot_sinEnlace_soloMensaje() {
        when(authService.forgotPassword("no@example.com")).thenReturn(null);

        ResponseEntity<Map<String, String>> res =
                controller.forgot(new AuthController.ForgotRequest("no@example.com"));

        assertThat(res.getBody()).containsKey("message");
        assertThat(res.getBody()).doesNotContainKey("resetLink");
    }

    // ---------- /reset ----------

    @Test
    @DisplayName("reset: delega en resetPassword y confirma con un mensaje")
    void reset_delegaEnResetPassword() {
        ResponseEntity<Map<String, String>> res =
                controller.reset(new AuthController.ResetRequest("tok", "password123"));

        verify(authService).resetPassword("tok", "password123");
        assertThat(res.getBody()).containsKey("message");
    }

    // ---------- /me ----------

    @Test
    @DisplayName("me: delega getProfile con el nombre de la autenticación y arma ProfileDto")
    void me_delegaGetProfile() {
        User user = usuario("user@example.com", "Juan", User.Role.VECINO);
        when(authService.getProfile("user@example.com")).thenReturn(user);

        ResponseEntity<AuthController.ProfileDto> res = controller.me(authConNombre("user@example.com"));

        assertThat(res.getBody().email()).isEqualTo("user@example.com");
        assertThat(res.getBody().name()).isEqualTo("Juan");
        assertThat(res.getBody().estadoValidacion()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("updateMe: delega updateProfile con los datos del request")
    void updateMe_delegaUpdateProfile() {
        User user = usuario("user@example.com", "Juan Perez", User.Role.VECINO);
        user.setTelefono("912345678");
        user.setNotificacionesActivas(true);
        when(authService.updateProfile("user@example.com", "Juan Perez", "912345678", true))
                .thenReturn(user);

        ResponseEntity<AuthController.ProfileDto> res = controller.updateMe(
                authConNombre("user@example.com"),
                new AuthController.ProfileUpdateRequest("Juan Perez", "912345678", true));

        assertThat(res.getBody().name()).isEqualTo("Juan Perez");
        assertThat(res.getBody().telefono()).isEqualTo("912345678");
        assertThat(res.getBody().notificacionesActivas()).isTrue();
    }

    // ---------- /vecinos (requieren admin) ----------

    @Test
    @DisplayName("vecinos: admin obtiene la lista mapeada a VecinoDto")
    void vecinos_admin_devuelveLista() {
        User v = usuario("v@example.com", "Vecino", User.Role.VECINO);
        when(authService.listVecinos()).thenReturn(List.of(v));

        List<AuthController.VecinoDto> res = controller.vecinos(authConRol("COMMUNITY_ADMIN"));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).email()).isEqualTo("v@example.com");
        assertThat(res.get(0).estadoValidacion()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("vecinos: un vecino sin rol admin recibe 403")
    void vecinos_noAdmin_lanza403() {
        assertThatThrownBy(() -> controller.vecinos(authConRol("VECINO")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dirigentes");
    }

    @Test
    @DisplayName("validar: admin marca al vecino como VALIDADO")
    void validar_admin_delegaSetValidacion() {
        UUID id = UUID.randomUUID();
        User v = usuario("v@example.com", "Vecino", User.Role.VECINO);
        v.setEstadoValidacion(User.EstadoValidacion.VALIDADO);
        when(authService.setValidacion(id, User.EstadoValidacion.VALIDADO)).thenReturn(v);

        AuthController.VecinoDto res = controller.validar(id, authConRol("PLATFORM_ADMIN"));

        assertThat(res.estadoValidacion()).isEqualTo("VALIDADO");
    }

    @Test
    @DisplayName("revocar: admin marca al vecino como PENDIENTE")
    void revocar_admin_delegaSetValidacion() {
        UUID id = UUID.randomUUID();
        User v = usuario("v@example.com", "Vecino", User.Role.VECINO);
        v.setEstadoValidacion(User.EstadoValidacion.PENDIENTE);
        when(authService.setValidacion(id, User.EstadoValidacion.PENDIENTE)).thenReturn(v);

        AuthController.VecinoDto res = controller.revocar(id, authConRol("COMMUNITY_ADMIN"));

        assertThat(res.estadoValidacion()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("editarVecino: admin delega updateVecino con los campos del request")
    void editarVecino_admin_delegaUpdateVecino() {
        UUID id = UUID.randomUUID();
        User v = usuario("nuevo@example.com", "Nuevo", User.Role.VECINO);
        when(authService.updateVecino(id, "Nuevo", "111", "Calle 1", "nuevo@example.com")).thenReturn(v);

        AuthController.VecinoDto res = controller.editarVecino(
                id,
                new AuthController.VecinoUpdateRequest("Nuevo", "111", "Calle 1", "nuevo@example.com"),
                authConRol("COMMUNITY_ADMIN"));

        assertThat(res.email()).isEqualTo("nuevo@example.com");
        assertThat(res.name()).isEqualTo("Nuevo");
    }

    @Test
    @DisplayName("eliminarVecino: admin delega deleteVecino")
    void eliminarVecino_admin_delegaDeleteVecino() {
        UUID id = UUID.randomUUID();

        controller.eliminarVecino(id, authConRol("COMMUNITY_ADMIN"));

        verify(authService).deleteVecino(id);
    }

    @Test
    @DisplayName("acceso: admin delega setAcceso con el flag del request")
    void acceso_admin_delegaSetAcceso() {
        UUID id = UUID.randomUUID();
        User v = usuario("v@example.com", "Vecino", User.Role.VECINO);
        v.setAccesoAprobado(true);
        when(authService.setAcceso(eq(id), eq(true))).thenReturn(v);

        AuthController.VecinoDto res = controller.acceso(
                id, new AuthController.AccesoRequest(true), authConRol("COMMUNITY_ADMIN"));

        assertThat(res.accesoAprobado()).isTrue();
    }

    // ---------- manejador de excepciones ----------

    @Test
    @DisplayName("handleInvalidToken: convierte IllegalArgumentException en 401 con el mensaje")
    void handleInvalidToken_devuelve401ConMensaje() {
        ResponseEntity<Map<String, String>> res =
                controller.handleInvalidToken(new IllegalArgumentException("idToken inválido"));

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(res.getBody()).containsEntry("error", "idToken inválido");
    }

    @Test
    @DisplayName("handleInvalidToken: mensaje nulo cae en el texto por defecto")
    void handleInvalidToken_mensajeNulo_usaDefault() {
        ResponseEntity<Map<String, String>> res =
                controller.handleInvalidToken(new IllegalArgumentException((String) null));

        assertThat(res.getBody()).containsEntry("error", "Solicitud inválida");
    }
}
