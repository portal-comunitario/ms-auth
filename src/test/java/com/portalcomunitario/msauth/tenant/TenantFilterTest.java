package com.portalcomunitario.msauth.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantFilterTest {

    private final TenantFilter filter = new TenantFilter();

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    /** Captura el tenant vigente DURANTE la ejecución de la cadena. */
    private String tenantDuranteCadena(HttpServletRequest req) throws Exception {
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        String[] holder = new String[1];
        doAnswer(inv -> { holder[0] = TenantContext.getCurrentTenant(); return null; })
                .when(chain).doFilter(req, res);
        filter.doFilterInternal(req, res, chain);
        return holder[0];
    }

    @Test
    void usaElHeaderXTenantIdCuandoEstaPresente() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Tenant-ID")).thenReturn("villa_el_sol");
        assertThat(tenantDuranteCadena(req)).isEqualTo("villa_el_sol");
        assertThat(TenantContext.getCurrentTenant()).isNull(); // se limpia al terminar
    }

    @Test
    void usaElSubdominioCuandoNoHayHeader() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Tenant-ID")).thenReturn(null);
        when(req.getServerName()).thenReturn("villa.portal.cl");
        assertThat(tenantDuranteCadena(req)).isEqualTo("villa");
    }

    @Test
    void caeAPublicCuandoNoHayHeaderNiSubdominio() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Tenant-ID")).thenReturn(null);
        when(req.getServerName()).thenReturn("localhost");
        assertThat(tenantDuranteCadena(req)).isEqualTo("public");
    }
}
