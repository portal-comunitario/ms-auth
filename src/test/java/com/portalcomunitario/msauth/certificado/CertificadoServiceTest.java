package com.portalcomunitario.msauth.certificado;

import com.portalcomunitario.msauth.messaging.NotificacionEvento;
import com.portalcomunitario.msauth.messaging.NotificacionPublisher;
import com.portalcomunitario.msauth.messaging.RabbitConfig;
import com.portalcomunitario.msauth.user.User;
import com.portalcomunitario.msauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificadoServiceTest {

    @Mock private SolicitudCertificadoRepository solicitudRepo;
    @Mock private CertificadoArchivoRepository archivoRepo;
    @Mock private UserRepository userRepository;
    @Mock private PdfCertificadoGenerator pdfGenerator;
    @Mock private NotificacionPublisher notificacionPublisher;

    private CertificadoService service;

    @BeforeEach
    void setUp() {
        service = new CertificadoService(solicitudRepo, archivoRepo, userRepository, pdfGenerator,
                "Junta de Vecinos Villa Las Flores", "Av. Lo Errázuriz 3940", "Maipú", notificacionPublisher);
    }

    // --- helpers ---------------------------------------------------------

    private static void setId(SolicitudCertificado s, UUID id) {
        try {
            Field f = SolicitudCertificado.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private User vecino(UUID id, String nombre, String email) {
        User u = new User();
        try {
            Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        u.setName(nombre);
        u.setEmail(email);
        return u;
    }

    private SolicitudCertificado solicitud(UUID id, UUID vecinoId, String email, EstadoCertificado estado) {
        SolicitudCertificado s = new SolicitudCertificado();
        setId(s, id);
        s.setVecinoId(vecinoId);
        s.setVecinoEmail(email);
        s.setVecinoNombre("Juan Vecino");
        s.setRut("12.345.678-5");
        s.setDireccion("Calle Falsa 123");
        s.setMotivo("Trámite bancario");
        s.setEstado(estado);
        return s;
    }

    // --- crear -----------------------------------------------------------

    @Test
    @DisplayName("crear: usuario inexistente lanza 401")
    void crear_usuarioNoEncontrado_lanza401() {
        when(userRepository.findByEmail("no@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear("no@example.com", "motivo", "12.345.678-5", "dir",
                mock(MultipartFile.class), mock(MultipartFile.class)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Usuario no encontrado");
        verify(solicitudRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: cédula vacía lanza 400 (adjuntos obligatorios)")
    void crear_sinAdjuntos_lanza400() {
        when(userRepository.findByEmail("u@example.com"))
                .thenReturn(Optional.of(vecino(UUID.randomUUID(), "Juan", "u@example.com")));
        MultipartFile cedula = mock(MultipartFile.class);
        when(cedula.isEmpty()).thenReturn(true);
        MultipartFile comprobante = mock(MultipartFile.class);

        assertThatThrownBy(() -> service.crear("u@example.com", "m", "12.345.678-5", "dir", cedula, comprobante))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("adjuntar");
        verify(solicitudRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: RUT inválido lanza 400")
    void crear_rutInvalido_lanza400() {
        when(userRepository.findByEmail("u@example.com"))
                .thenReturn(Optional.of(vecino(UUID.randomUUID(), "Juan", "u@example.com")));
        MultipartFile cedula = mock(MultipartFile.class);
        MultipartFile comprobante = mock(MultipartFile.class);

        assertThatThrownBy(() -> service.crear("u@example.com", "m", "12.345.678-9", "dir", cedula, comprobante))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no es válido");
        verify(solicitudRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: camino feliz guarda la solicitud (SOLICITADO, RUT formateado) y ambos archivos")
    void crear_exitoso_guardaSolicitudYArchivos() throws IOException {
        User u = vecino(UUID.randomUUID(), "Juan Vecino", "u@example.com");
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(u));
        MultipartFile cedula = mock(MultipartFile.class);
        when(cedula.getContentType()).thenReturn("image/png");
        when(cedula.getOriginalFilename()).thenReturn("cedula.png");
        when(cedula.getBytes()).thenReturn(new byte[]{1, 2});
        MultipartFile comprobante = mock(MultipartFile.class);
        when(comprobante.getContentType()).thenReturn("application/pdf");
        when(comprobante.getOriginalFilename()).thenReturn("comprobante.pdf");
        when(comprobante.getBytes()).thenReturn(new byte[]{3, 4});
        when(solicitudRepo.save(any(SolicitudCertificado.class))).thenAnswer(inv -> inv.getArgument(0));

        SolicitudCertificado res = service.crear("u@example.com", "Trámite", "123456785", "Calle 1", cedula, comprobante);

        assertThat(res.getEstado()).isEqualTo(EstadoCertificado.SOLICITADO);
        assertThat(res.getRut()).isEqualTo("12.345.678-5"); // normalizado por RutValidator.formatear
        assertThat(res.getVecinoEmail()).isEqualTo("u@example.com");
        assertThat(res.getVecinoNombre()).isEqualTo("Juan Vecino");
        assertThat(res.getMotivo()).isEqualTo("Trámite");
        assertThat(res.getDireccion()).isEqualTo("Calle 1");

        ArgumentCaptor<CertificadoArchivo> captor = ArgumentCaptor.forClass(CertificadoArchivo.class);
        verify(archivoRepo, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CertificadoArchivo::getTipo)
                .containsExactly(TipoArchivo.CEDULA, TipoArchivo.COMPROBANTE);
    }

    @Test
    @DisplayName("crear: si falla la lectura del archivo lanza 400")
    void crear_errorAlLeerArchivo_lanza400() throws IOException {
        User u = vecino(UUID.randomUUID(), "Juan", "u@example.com");
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(u));
        MultipartFile cedula = mock(MultipartFile.class);
        when(cedula.getBytes()).thenThrow(new IOException("boom"));
        MultipartFile comprobante = mock(MultipartFile.class);
        when(solicitudRepo.save(any(SolicitudCertificado.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.crear("u@example.com", "m", "123456785", "dir", cedula, comprobante))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se pudo leer el archivo");
    }

    // --- listados --------------------------------------------------------

    @Test
    @DisplayName("misSolicitudes: delega en el repositorio")
    void misSolicitudes_delega() {
        SolicitudCertificado s = solicitud(UUID.randomUUID(), UUID.randomUUID(), "u@example.com", EstadoCertificado.SOLICITADO);
        when(solicitudRepo.findByVecinoEmailOrderByFechaSolicitudDesc("u@example.com")).thenReturn(List.of(s));

        assertThat(service.misSolicitudes("u@example.com")).containsExactly(s);
    }

    @Test
    @DisplayName("pendientes: consulta las solicitudes en estado SOLICITADO")
    void pendientes_delega() {
        SolicitudCertificado s = solicitud(UUID.randomUUID(), UUID.randomUUID(), "u@example.com", EstadoCertificado.SOLICITADO);
        when(solicitudRepo.findByEstadoOrderByFechaSolicitudAsc(EstadoCertificado.SOLICITADO)).thenReturn(List.of(s));

        assertThat(service.pendientes()).containsExactly(s);
    }

    @Test
    @DisplayName("todas: delega en findAllByOrderByFechaSolicitudDesc")
    void todas_delega() {
        SolicitudCertificado s = solicitud(UUID.randomUUID(), UUID.randomUUID(), "u@example.com", EstadoCertificado.EMITIDO);
        when(solicitudRepo.findAllByOrderByFechaSolicitudDesc()).thenReturn(List.of(s));

        assertThat(service.todas()).containsExactly(s);
    }

    // --- get -------------------------------------------------------------

    @Test
    @DisplayName("get: existente devuelve la solicitud")
    void get_existente() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, UUID.randomUUID(), "u@example.com", EstadoCertificado.SOLICITADO);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));

        assertThat(service.get(id)).isSameAs(s);
    }

    @Test
    @DisplayName("get: inexistente lanza 404")
    void get_noExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(solicitudRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solicitud no encontrada");
    }

    // --- aprobar ---------------------------------------------------------

    @Test
    @DisplayName("aprobar: si ya está EMITIDO devuelve la solicitud sin regenerar")
    void aprobar_yaEmitido_noHaceNada() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, UUID.randomUUID(), "u@example.com", EstadoCertificado.EMITIDO);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));

        SolicitudCertificado res = service.aprobar(id);

        assertThat(res).isSameAs(s);
        verify(pdfGenerator, never()).generar(any(), any(), any(), any(), any(), any(), any(), any());
        verify(solicitudRepo, never()).save(any());
        verify(notificacionPublisher, never()).publicar(anyString(), any());
    }

    @Test
    @DisplayName("aprobar: camino feliz emite folio, guarda PDF, valida al vecino y notifica")
    void aprobar_exitoso_conVecino() {
        UUID id = UUID.randomUUID();
        UUID vecinoId = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, vecinoId, "u@example.com", EstadoCertificado.SOLICITADO);
        User u = vecino(vecinoId, "Juan Vecino", "u@example.com");
        u.setTelefono("+56911111111");
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));
        when(userRepository.findById(vecinoId)).thenReturn(Optional.of(u));
        when(solicitudRepo.nextFolio()).thenReturn(7L);
        when(pdfGenerator.generar(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{9});
        when(archivoRepo.findBySolicitudIdAndTipo(id, TipoArchivo.PDF)).thenReturn(Optional.empty());

        SolicitudCertificado res = service.aprobar(id);

        assertThat(res.getEstado()).isEqualTo(EstadoCertificado.EMITIDO);
        assertThat(res.getFolio()).isNotBlank().contains("VLF-").contains("000007");
        assertThat(res.getFechaResolucion()).isNotNull();
        assertThat(res.getMotivoRechazo()).isNull();
        assertThat(u.getEstadoValidacion()).isEqualTo(User.EstadoValidacion.VALIDADO);
        assertThat(u.getDireccion()).isEqualTo("Calle Falsa 123");
        assertThat(u.getRut()).isEqualTo("12.345.678-5");

        // Se persiste un archivo PDF nuevo
        ArgumentCaptor<CertificadoArchivo> captor = ArgumentCaptor.forClass(CertificadoArchivo.class);
        verify(archivoRepo).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoArchivo.PDF);
        assertThat(captor.getValue().getContentType()).isEqualTo("application/pdf");
        verify(userRepository).save(u);
        verify(solicitudRepo).save(s);
        verify(notificacionPublisher).publicar(eq(RabbitConfig.RK_CERTIFICADO_EMITIDO), any(NotificacionEvento.class));
    }

    @Test
    @DisplayName("aprobar: sin vecino usa datos de la solicitud, reutiliza PDF existente y no notifica")
    void aprobar_sinVecino_noNotifica() {
        UUID id = UUID.randomUUID();
        UUID vecinoId = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, vecinoId, "u@example.com", EstadoCertificado.SOLICITADO);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));
        when(userRepository.findById(vecinoId)).thenReturn(Optional.empty());
        when(solicitudRepo.nextFolio()).thenReturn(1L);
        when(pdfGenerator.generar(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});
        CertificadoArchivo existente = new CertificadoArchivo();
        when(archivoRepo.findBySolicitudIdAndTipo(id, TipoArchivo.PDF)).thenReturn(Optional.of(existente));

        SolicitudCertificado res = service.aprobar(id);

        assertThat(res.getEstado()).isEqualTo(EstadoCertificado.EMITIDO);
        verify(archivoRepo).save(existente); // reutiliza el archivo existente
        verify(userRepository, never()).save(any());
        verify(notificacionPublisher, never()).publicar(anyString(), any());
    }

    @Test
    @DisplayName("aprobar: vecino sin email no dispara notificación pero sí lo valida")
    void aprobar_vecinoSinEmail_noNotifica() {
        UUID id = UUID.randomUUID();
        UUID vecinoId = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, vecinoId, "u@example.com", EstadoCertificado.SOLICITADO);
        User u = vecino(vecinoId, "Juan", null); // email nulo
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));
        when(userRepository.findById(vecinoId)).thenReturn(Optional.of(u));
        when(solicitudRepo.nextFolio()).thenReturn(2L);
        when(pdfGenerator.generar(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});
        when(archivoRepo.findBySolicitudIdAndTipo(id, TipoArchivo.PDF)).thenReturn(Optional.empty());

        service.aprobar(id);

        verify(userRepository).save(u);
        verify(notificacionPublisher, never()).publicar(anyString(), any());
    }

    // --- rechazar --------------------------------------------------------

    @Test
    @DisplayName("rechazar: marca RECHAZADO con motivo y fecha de resolución")
    void rechazar_exitoso() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, UUID.randomUUID(), "u@example.com", EstadoCertificado.SOLICITADO);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));
        when(solicitudRepo.save(s)).thenReturn(s);

        SolicitudCertificado res = service.rechazar(id, "Documentos ilegibles");

        assertThat(res.getEstado()).isEqualTo(EstadoCertificado.RECHAZADO);
        assertThat(res.getMotivoRechazo()).isEqualTo("Documentos ilegibles");
        assertThat(res.getFechaResolucion()).isNotNull();
    }

    @Test
    @DisplayName("rechazar: solicitud inexistente lanza 404")
    void rechazar_noExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(solicitudRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rechazar(id, "x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solicitud no encontrada");
    }

    // --- archivo ---------------------------------------------------------

    @Test
    @DisplayName("archivo: devuelve el archivo cuando existe")
    void archivo_existente() {
        UUID id = UUID.randomUUID();
        CertificadoArchivo a = new CertificadoArchivo();
        when(archivoRepo.findBySolicitudIdAndTipo(id, TipoArchivo.PDF)).thenReturn(Optional.of(a));

        assertThat(service.archivo(id, TipoArchivo.PDF)).isSameAs(a);
    }

    @Test
    @DisplayName("archivo: inexistente lanza 404")
    void archivo_noExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(archivoRepo.findBySolicitudIdAndTipo(id, TipoArchivo.CEDULA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archivo(id, TipoArchivo.CEDULA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Archivo no encontrado");
    }

    // --- eliminar --------------------------------------------------------

    @Test
    @DisplayName("eliminar: un admin puede borrar cualquier solicitud")
    void eliminar_admin_borra() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, UUID.randomUUID(), "otro@example.com", EstadoCertificado.EMITIDO);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));

        service.eliminar(id, "admin@example.com", true);

        verify(archivoRepo).deleteBySolicitudId(id);
        verify(solicitudRepo).delete(s);
    }

    @Test
    @DisplayName("eliminar: el dueño puede borrar la suya (email case-insensitive)")
    void eliminar_dueno_borra() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, UUID.randomUUID(), "Duenio@Example.com", EstadoCertificado.SOLICITADO);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));

        service.eliminar(id, "duenio@example.com", false);

        verify(archivoRepo).deleteBySolicitudId(id);
        verify(solicitudRepo).delete(s);
    }

    @Test
    @DisplayName("eliminar: un no-dueño no admin recibe 403")
    void eliminar_ajeno_lanza403() {
        UUID id = UUID.randomUUID();
        SolicitudCertificado s = solicitud(id, UUID.randomUUID(), "otro@example.com", EstadoCertificado.SOLICITADO);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.eliminar(id, "intruso@example.com", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No puedes eliminar");
        verify(archivoRepo, never()).deleteBySolicitudId(any());
        verify(solicitudRepo, never()).delete(any());
    }

    // Mockito.mock estático reexpuesto para brevedad en los helpers de arriba.
    private static <T> T mock(Class<T> c) {
        return org.mockito.Mockito.mock(c);
    }
}
