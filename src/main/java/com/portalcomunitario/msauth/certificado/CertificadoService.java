package com.portalcomunitario.msauth.certificado;

import com.portalcomunitario.msauth.user.User;
import com.portalcomunitario.msauth.user.UserRepository;
import com.portalcomunitario.msauth.messaging.Destinatario;
import com.portalcomunitario.msauth.messaging.NotificacionEvento;
import com.portalcomunitario.msauth.messaging.NotificacionPublisher;
import com.portalcomunitario.msauth.messaging.RabbitConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class CertificadoService {

    private final SolicitudCertificadoRepository solicitudRepo;
    private final CertificadoArchivoRepository archivoRepo;
    private final UserRepository userRepository;
    private final PdfCertificadoGenerator pdfGenerator;
    private final String junta;
    private final String sede;
    private final String comuna;
    private final NotificacionPublisher notificacionPublisher;

    public CertificadoService(SolicitudCertificadoRepository solicitudRepo,
                              CertificadoArchivoRepository archivoRepo,
                              UserRepository userRepository,
                              PdfCertificadoGenerator pdfGenerator,
                              @Value("${app.community.junta:Junta de Vecinos Villa Las Flores}") String junta,
                              @Value("${app.community.sede:Av. Lo Errázuriz 3940}") String sede,
                              @Value("${app.community.comuna:Maipú}") String comuna,
                              NotificacionPublisher notificacionPublisher) {
        this.solicitudRepo = solicitudRepo;
        this.archivoRepo = archivoRepo;
        this.userRepository = userRepository;
        this.pdfGenerator = pdfGenerator;
        this.junta = junta;
        this.sede = sede;
        this.comuna = comuna;
        this.notificacionPublisher = notificacionPublisher;
    }

    public SolicitudCertificado crear(String email, String motivo, String rut, String direccion,
                                      MultipartFile cedula, MultipartFile comprobante) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (cedula == null || cedula.isEmpty() || comprobante == null || comprobante.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes adjuntar la cédula y el comprobante de domicilio");
        }
        if (!RutValidator.valido(rut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El RUT ingresado no es válido");
        }
        rut = RutValidator.formatear(rut);
        SolicitudCertificado s = new SolicitudCertificado();
        s.setVecinoId(user.getId());
        s.setVecinoEmail(user.getEmail());
        s.setVecinoNombre(user.getName());
        s.setMotivo(motivo);
        s.setRut(rut);
        s.setDireccion(direccion);
        s.setEstado(EstadoCertificado.SOLICITADO);
        s = solicitudRepo.save(s);
        guardarArchivo(s.getId(), TipoArchivo.CEDULA, cedula);
        guardarArchivo(s.getId(), TipoArchivo.COMPROBANTE, comprobante);
        return s;
    }

    private void guardarArchivo(UUID solicitudId, TipoArchivo tipo, MultipartFile file) {
        CertificadoArchivo a = new CertificadoArchivo();
        a.setSolicitudId(solicitudId);
        a.setTipo(tipo);
        a.setContentType(file.getContentType());
        a.setFilename(file.getOriginalFilename());
        try {
            a.setData(file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo " + tipo);
        }
        archivoRepo.save(a);
    }

    public List<SolicitudCertificado> misSolicitudes(String email) {
        return solicitudRepo.findByVecinoEmailOrderByFechaSolicitudDesc(email);
    }

    public List<SolicitudCertificado> pendientes() {
        return solicitudRepo.findByEstadoOrderByFechaSolicitudAsc(EstadoCertificado.SOLICITADO);
    }

    public List<SolicitudCertificado> todas() {
        return solicitudRepo.findAllByOrderByFechaSolicitudDesc();
    }

    public SolicitudCertificado aprobar(UUID id) {
        SolicitudCertificado s = get(id);
        if (s.getEstado() == EstadoCertificado.EMITIDO) return s;
        User vecino = userRepository.findById(s.getVecinoId()).orElse(null);
        String nombre = vecino != null ? vecino.getName() : s.getVecinoNombre();
        String rut = s.getRut() != null && !s.getRut().isBlank() ? s.getRut() : (vecino != null ? vecino.getRut() : null);
        String direccion = s.getDireccion() != null ? s.getDireccion() : (vecino != null ? vecino.getDireccion() : null);

        String folio = String.format("VLF-%d-%06d-%s", Year.now().getValue(), solicitudRepo.nextFolio(), tokenAleatorio());
        byte[] pdf = pdfGenerator.generar(nombre, rut, direccion, folio, junta, sede, comuna, s.getMotivo());

        CertificadoArchivo pdfArchivo = archivoRepo.findBySolicitudIdAndTipo(id, TipoArchivo.PDF)
                .orElseGet(CertificadoArchivo::new);
        pdfArchivo.setSolicitudId(id);
        pdfArchivo.setTipo(TipoArchivo.PDF);
        pdfArchivo.setContentType("application/pdf");
        pdfArchivo.setFilename("certificado-" + folio + ".pdf");
        pdfArchivo.setData(pdf);
        archivoRepo.save(pdfArchivo);

        s.setEstado(EstadoCertificado.EMITIDO);
        s.setFolio(folio);
        s.setMotivoRechazo(null);
        s.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(s);

        // Aprobar la solicitud valida al vecino.
        if (vecino != null) {
            vecino.setEstadoValidacion(User.EstadoValidacion.VALIDADO);
            if (s.getDireccion() != null && !s.getDireccion().isBlank()) vecino.setDireccion(s.getDireccion());
            if (s.getRut() != null && !s.getRut().isBlank()) vecino.setRut(s.getRut());
            userRepository.save(vecino);
        }
        if (vecino != null && vecino.getEmail() != null) {
            NotificacionEvento evento = new NotificacionEvento(
                    "CERTIFICADO_EMITIDO",
                    "Tu certificado de residencia está listo",
                    "Hola " + nombre + ", tu certificado de residencia (folio " + folio + ") ya fue emitido. "
                            + "Puedes descargarlo desde el portal, en la sección Trámites.",
                    java.util.List.of(new Destinatario(nombre, vecino.getEmail(), vecino.getTelefono(), true)));
            notificacionPublisher.publicar(RabbitConfig.RK_CERTIFICADO_EMITIDO, evento);
        }
        return s;
    }

    public SolicitudCertificado rechazar(UUID id, String motivo) {
        SolicitudCertificado s = get(id);
        s.setEstado(EstadoCertificado.RECHAZADO);
        s.setMotivoRechazo(motivo);
        s.setFechaResolucion(LocalDateTime.now());
        return solicitudRepo.save(s);
    }

    public CertificadoArchivo archivo(UUID solicitudId, TipoArchivo tipo) {
        return archivoRepo.findBySolicitudIdAndTipo(solicitudId, tipo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado"));
    }

    @org.springframework.transaction.annotation.Transactional
    public void eliminar(UUID id, String email, boolean admin) {
        SolicitudCertificado s = get(id);
        if (!admin && (s.getVecinoEmail() == null || !s.getVecinoEmail().equalsIgnoreCase(email))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes eliminar esta solicitud");
        }
        archivoRepo.deleteBySolicitudId(id);
        solicitudRepo.delete(s);
    }

    private static final java.security.SecureRandom RAND = new java.security.SecureRandom();
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin caracteres ambiguos

    private String tokenAleatorio() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(ALFABETO.charAt(RAND.nextInt(ALFABETO.length())));
        return sb.toString();
    }

    public SolicitudCertificado get(UUID id) {
        return solicitudRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
    }
}
