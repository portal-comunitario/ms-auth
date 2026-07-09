package com.portalcomunitario.msauth.certificado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificadoControllerTest {

    @Mock private CertificadoService service;

    private CertificadoController controller;

    @BeforeEach
    void setUp() {
        controller = new CertificadoController(service);
    }

    // --- helpers ---------------------------------------------------------

    private JwtAuthenticationToken jwtAuth(String email, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(email)
                .claim("email", email)
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private SolicitudCertificado solicitud(UUID id, String email, EstadoCertificado estado) {
        SolicitudCertificado s = new SolicitudCertificado();
        try {
            Field f = SolicitudCertificado.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        s.setVecinoEmail(email);
        s.setVecinoNombre("Juan Vecino");
        s.setEstado(estado);
        return s;
    }

    // --- crear -----------------------------------------------------------

    @Test
    @DisplayName("crear: delega en el servicio con el email del JWT y devuelve el DTO")
    void crear_delegaYDevuelveDto() {
        MultipartFile cedula = mock(MultipartFile.class);
        MultipartFile comprobante = mock(MultipartFile.class);
        SolicitudCertificado s = solicitud(UUID.randomUUID(), "u@example.com", EstadoCertificado.SOLICITADO);
        when(service.crear("u@example.com", "motivo", "rut", "dir", cedula, comprobante)).thenReturn(s);

        SolicitudCertificadoDto dto = controller.crear("motivo", "rut", "dir", cedula, comprobante,
                jwtAuth("u@example.com", "VECINO"));

        assertThat(dto.vecinoEmail()).isEqualTo("u@example.com");
        assertThat(dto.estado()).isEqualTo("SOLICITADO");
        verify(service).crear("u@example.com", "motivo", "rut", "dir", cedula, comprobante);
    }

    // --- mias ------------------------------------------------------------

    @Test
    @DisplayName("mias: mapea las solicitudes del vecino a DTOs")
    void mias_mapeaDtos() {
        SolicitudCertificado s = solicitud(UUID.randomUUID(), "u@example.com", EstadoCertificado.EMITIDO);
        when(service.misSolicitudes("u@example.com")).thenReturn(List.of(s));

        List<SolicitudCertificadoDto> res = controller.mias(jwtAuth("u@example.com", "VECINO"));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).estado()).isEqualTo("EMITIDO");
        assertThat(res.get(0).tienePdf()).isTrue();
    }

    @Test
    @DisplayName("mias: con Authentication no-JWT usa getName() como email")
    void mias_authNoJwt_usaGetName() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("plain@example.com");
        when(service.misSolicitudes("plain@example.com")).thenReturn(List.of());

        assertThat(controller.mias(auth)).isEmpty();
    }

    @Test
    @DisplayName("mias: sin JWT y sin nombre lanza 401")
    void mias_sinIdentidad_lanza401() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("  ");

        assertThatThrownBy(() -> controller.mias(auth))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se pudo determinar el usuario");
    }

    // --- pendientes ------------------------------------------------------

    @Test
    @DisplayName("pendientes: admin obtiene la lista")
    void pendientes_admin_ok() {
        SolicitudCertificado s = solicitud(UUID.randomUUID(), "u@example.com", EstadoCertificado.SOLICITADO);
        when(service.pendientes()).thenReturn(List.of(s));

        List<SolicitudCertificadoDto> res = controller.pendientes(jwtAuth("admin@example.com", "COMMUNITY_ADMIN"));

        assertThat(res).hasSize(1);
    }

    @Test
    @DisplayName("pendientes: un vecino recibe 403")
    void pendientes_noAdmin_lanza403() {
        assertThatThrownBy(() -> controller.pendientes(jwtAuth("u@example.com", "VECINO")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solo los dirigentes");
        verify(service, never()).pendientes();
    }

    // --- aprobar ---------------------------------------------------------

    @Test
    @DisplayName("aprobar: admin (PLATFORM_ADMIN) delega y devuelve DTO")
    void aprobar_admin_ok() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, "u@example.com", EstadoCertificado.EMITIDO);
        when(service.aprobar(id)).thenReturn(s);

        SolicitudCertificadoDto dto = controller.aprobar(id, jwtAuth("admin@example.com", "PLATFORM_ADMIN"));

        assertThat(dto.estado()).isEqualTo("EMITIDO");
        verify(service).aprobar(id);
    }

    @Test
    @DisplayName("aprobar: vecino recibe 403")
    void aprobar_noAdmin_lanza403() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> controller.aprobar(id, jwtAuth("u@example.com", "VECINO")))
                .isInstanceOf(ResponseStatusException.class);
        verify(service, never()).aprobar(id);
    }

    // --- rechazar --------------------------------------------------------

    @Test
    @DisplayName("rechazar: admin con motivo en el body delega correctamente")
    void rechazar_admin_conMotivo() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, "u@example.com", EstadoCertificado.RECHAZADO);
        when(service.rechazar(id, "ilegible")).thenReturn(s);

        SolicitudCertificadoDto dto = controller.rechazar(id, Map.of("motivo", "ilegible"),
                jwtAuth("admin@example.com", "COMMUNITY_ADMIN"));

        assertThat(dto.estado()).isEqualTo("RECHAZADO");
        verify(service).rechazar(id, "ilegible");
    }

    @Test
    @DisplayName("rechazar: body nulo pasa motivo nulo al servicio")
    void rechazar_bodyNulo_motivoNulo() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, "u@example.com", EstadoCertificado.RECHAZADO);
        when(service.rechazar(id, null)).thenReturn(s);

        controller.rechazar(id, null, jwtAuth("admin@example.com", "COMMUNITY_ADMIN"));

        verify(service).rechazar(id, null);
    }

    @Test
    @DisplayName("rechazar: vecino recibe 403")
    void rechazar_noAdmin_lanza403() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> controller.rechazar(id, Map.of("motivo", "x"), jwtAuth("u@example.com", "VECINO")))
                .isInstanceOf(ResponseStatusException.class);
        verify(service, never()).rechazar(eq(id), org.mockito.ArgumentMatchers.any());
    }

    // --- eliminar --------------------------------------------------------

    @Test
    @DisplayName("eliminar: pasa email y flag admin=true para un admin")
    void eliminar_admin_delegaConFlag() {
        UUID id = UUID.randomUUID();

        controller.eliminar(id, jwtAuth("admin@example.com", "COMMUNITY_ADMIN"));

        verify(service).eliminar(id, "admin@example.com", true);
    }

    @Test
    @DisplayName("eliminar: pasa flag admin=false para un vecino")
    void eliminar_vecino_delegaSinFlag() {
        UUID id = UUID.randomUUID();

        controller.eliminar(id, jwtAuth("u@example.com", "VECINO"));

        verify(service).eliminar(id, "u@example.com", false);
    }

    // --- archivo ---------------------------------------------------------

    @Test
    @DisplayName("archivo: el dueño descarga el binario con content-type y filename")
    void archivo_dueno_devuelveBytes() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, "u@example.com", EstadoCertificado.EMITIDO);
        when(service.get(id)).thenReturn(s);
        CertificadoArchivo a = new CertificadoArchivo();
        a.setContentType("application/pdf");
        a.setFilename("certificado.pdf");
        a.setData(new byte[]{1, 2, 3});
        when(service.archivo(id, TipoArchivo.PDF)).thenReturn(a);

        ResponseEntity<byte[]> res = controller.archivo(id, "pdf", jwtAuth("u@example.com", "VECINO"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(res.getHeaders().getFirst("Content-Disposition")).contains("certificado.pdf");
        assertThat(res.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("archivo: un admin puede ver el archivo de otro vecino")
    void archivo_admin_veAjeno() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, "otro@example.com", EstadoCertificado.EMITIDO);
        when(service.get(id)).thenReturn(s);
        CertificadoArchivo a = new CertificadoArchivo();
        a.setData(new byte[]{9});
        when(service.archivo(id, TipoArchivo.CEDULA)).thenReturn(a);

        ResponseEntity<byte[]> res = controller.archivo(id, "cedula", jwtAuth("admin@example.com", "COMMUNITY_ADMIN"));

        // sin content-type en el archivo -> octet-stream por defecto
        assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(res.getBody()).containsExactly(9);
    }

    @Test
    @DisplayName("archivo: tipo de archivo inválido lanza 400")
    void archivo_tipoInvalido_lanza400() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, "u@example.com", EstadoCertificado.EMITIDO);
        when(service.get(id)).thenReturn(s);

        assertThatThrownBy(() -> controller.archivo(id, "xxx", jwtAuth("u@example.com", "VECINO")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Tipo de archivo inválido");
    }

    @Test
    @DisplayName("archivo: un vecino ajeno (no admin) recibe 403")
    void archivo_ajeno_lanza403() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, "otro@example.com", EstadoCertificado.EMITIDO);
        when(service.get(id)).thenReturn(s);

        assertThatThrownBy(() -> controller.archivo(id, "pdf", jwtAuth("intruso@example.com", "VECINO")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Acceso denegado");
        verify(service, never()).archivo(eq(id), org.mockito.ArgumentMatchers.any());
    }
}
