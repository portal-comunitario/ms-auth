package com.portalcomunitario.msauth.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void guardaYDevuelveElTenantActual() {
        TenantContext.setCurrentTenant("villa_el_sol");
        assertThat(TenantContext.getCurrentTenant()).isEqualTo("villa_el_sol");
    }

    @Test
    void clearDejaElContextoNulo() {
        TenantContext.setCurrentTenant("villa_el_sol");
        TenantContext.clear();
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void sinAsignar_devuelveNulo() {
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
