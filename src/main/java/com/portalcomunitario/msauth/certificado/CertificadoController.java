package com.portalcomunitario.msauth.certificado;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/certificados")
public class CertificadoController {

    private final CertificadoService service;

    public CertificadoController(CertificadoService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SolicitudCertificadoDto crear(@RequestParam(value = "motivo", required = false) String motivo,
                                         @RequestParam(value = "rut", required = false) String rut,
                                         @RequestParam(value = "direccion", required = false) String direccion,
                                         @RequestParam("cedula") MultipartFile cedula,
                                         @RequestParam("comprobante") MultipartFile comprobante,
                                         Authentication auth) {
        return SolicitudCertificadoDto.from(service.crear(extractEmail(auth), motivo, rut, direccion, cedula, comprobante));
    }

    @GetMapping("/mias")
    public List<SolicitudCertificadoDto> mias(Authentication auth) {
        return service.misSolicitudes(extractEmail(auth)).stream().map(SolicitudCertificadoDto::from).toList();
    }

    @GetMapping("/pendientes")
    public List<SolicitudCertificadoDto> pendientes(Authentication auth) {
        requireAdmin(auth);
        return service.pendientes().stream().map(SolicitudCertificadoDto::from).toList();
    }

    @GetMapping("/{id}/archivo/{tipo}")
    public ResponseEntity<byte[]> archivo(@PathVariable UUID id, @PathVariable String tipo, Authentication auth) {
        SolicitudCertificado s = service.get(id);
        if (!esAdmin(auth) && !s.getVecinoEmail().equalsIgnoreCase(extractEmail(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        TipoArchivo t;
        try {
            t = TipoArchivo.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de archivo inválido");
        }
        CertificadoArchivo a = service.archivo(id, t);
        MediaType mt = a.getContentType() != null ? MediaType.parseMediaType(a.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mt)
                .header("Content-Disposition", "inline; filename=\"" + (a.getFilename() != null ? a.getFilename() : "archivo") + "\"")
                .body(a.getData());
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, Authentication auth) {
        service.eliminar(id, extractEmail(auth), esAdmin(auth));
    }

    @PutMapping("/{id}/aprobar")
    public SolicitudCertificadoDto aprobar(@PathVariable UUID id, Authentication auth) {
        requireAdmin(auth);
        return SolicitudCertificadoDto.from(service.aprobar(id));
    }

    @PutMapping("/{id}/rechazar")
    public SolicitudCertificadoDto rechazar(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        requireAdmin(auth);
        String motivo = body != null ? body.get("motivo") : null;
        return SolicitudCertificadoDto.from(service.rechazar(id, motivo));
    }

    private String extractRole(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String r = jwtAuth.getToken().getClaimAsString("role");
            return r != null ? r : "VECINO";
        }
        return "VECINO";
    }

    private boolean esAdmin(Authentication auth) {
        String r = extractRole(auth);
        return "COMMUNITY_ADMIN".equals(r) || "PLATFORM_ADMIN".equals(r);
    }

    private void requireAdmin(Authentication auth) {
        if (!esAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los dirigentes pueden gestionar certificados");
        }
    }

    private String extractEmail(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String email = firstNonBlank(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
            if (email != null) return email;
        }
        String name = auth != null ? auth.getName() : null;
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se pudo determinar el usuario");
        }
        return name;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) { if (v != null && !v.isBlank()) return v; }
        return null;
    }
}
