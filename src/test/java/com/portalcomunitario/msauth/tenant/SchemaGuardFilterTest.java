package com.portalcomunitario.msauth.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaGuardFilterTest {

    private final SchemaGuardFilter filter = new SchemaGuardFilter();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarCon(String schemaClaim) {
        Jwt.Builder b = Jwt.withTokenValue("token").header("alg", "none");
        if (schemaClaim != null) b.claim("schema", schemaClaim);
        else b.claim("email", "x@example.com");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(b.build()));
    }

    @Test
    @DisplayName("token de otra comunidad → 403 y no continúa la cadena")
    void tokenDeOtraComunidad_devuelve403() throws Exception {
        autenticarCon("villa_el_sol");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Tenant-ID")).thenReturn("jardin_del_sur");

        filter.doFilterInternal(req, res, chain);

        verify(res).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    @DisplayName("token de la misma comunidad → continúa la cadena")
    void tokenMismaComunidad_pasa() throws Exception {
        autenticarCon("villa_el_sol");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Tenant-ID")).thenReturn("villa_el_sol");

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(res, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("token antiguo sin claim 'schema' → pasa (compatibilidad)")
    void tokenSinSchema_pasa() throws Exception {
        autenticarCon(null);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(res, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("sin autenticación → pasa (lo resuelve la cadena de seguridad)")
    void sinAutenticacion_pasa() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(res, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("misma comunidad resuelta por subdominio (sin header) → pasa")
    void mismaComunidadPorSubdominio_pasa() throws Exception {
        autenticarCon("villa");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Tenant-ID")).thenReturn(null);
        when(req.getServerName()).thenReturn("villa.portal.cl");

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(res, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("token public sin header ni subdominio → pasa")
    void tokenPublicSinHeader_pasa() throws Exception {
        autenticarCon("public");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Tenant-ID")).thenReturn(null);
        when(req.getServerName()).thenReturn("localhost");

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(res, never()).sendError(anyInt(), anyString());
    }
}
