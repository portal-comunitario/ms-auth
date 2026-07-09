package com.portalcomunitario.msauth.certificado;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lógica pura de validación/formato de RUT chileno (módulo 11).
 * RUTs válidos usados aquí (dígito verificador calculado a mano):
 *  - 12345678 -> DV 5   ("12.345.678-5")
 *  - 6        -> DV K    ("6-K",  resto == 10)
 *  - 31       -> DV 0    ("31-0", resto == 11)
 */
class RutValidatorTest {

    @Test
    @DisplayName("valido: null devuelve false")
    void valido_null_false() {
        assertThat(RutValidator.valido(null)).isFalse();
    }

    @Test
    @DisplayName("valido: cadena vacía devuelve false (longitud < 2)")
    void valido_vacio_false() {
        assertThat(RutValidator.valido("")).isFalse();
    }

    @Test
    @DisplayName("valido: un solo carácter devuelve false (longitud < 2)")
    void valido_unSoloCaracter_false() {
        assertThat(RutValidator.valido("9")).isFalse();
    }

    @ParameterizedTest
    @DisplayName("valido: RUT correcto sin formato")
    @ValueSource(strings = {"123456785", "6K", "310"})
    void valido_sinFormato_true(String rut) {
        assertThat(RutValidator.valido(rut)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("valido: RUT correcto con puntos y guion")
    @ValueSource(strings = {"12.345.678-5", "6-K", "31-0"})
    void valido_conFormato_true(String rut) {
        assertThat(RutValidator.valido(rut)).isTrue();
    }

    @Test
    @DisplayName("valido: dígito verificador K minúscula se normaliza (mayúscula) y es válido")
    void valido_kMinuscula_true() {
        assertThat(RutValidator.valido("6-k")).isTrue();
    }

    @Test
    @DisplayName("valido: dígito verificador incorrecto devuelve false")
    void valido_dvIncorrecto_false() {
        // 12345678 exige DV 5; ponemos 9.
        assertThat(RutValidator.valido("12.345.678-9")).isFalse();
    }

    @Test
    @DisplayName("valido: DV 'K' esperado pero se entrega otro devuelve false")
    void valido_dvKEsperadoPeroDistinto_false() {
        // 6 exige DV K; ponemos 1.
        assertThat(RutValidator.valido("6-1")).isFalse();
    }

    @Test
    @DisplayName("valido: cuerpo no numérico devuelve false")
    void valido_cuerpoNoNumerico_false() {
        assertThat(RutValidator.valido("12A45678-5")).isFalse();
    }

    @Test
    @DisplayName("valido: espacios internos y puntos/guion se ignoran (RUT válido)")
    void valido_conEspacios_true() {
        assertThat(RutValidator.valido(" 12 345 678-5 ")).isTrue();
    }

    @Test
    @DisplayName("formatear: agrupa en miles con puntos y guion antes del DV")
    void formatear_sinFormato_agrupaConPuntos() {
        assertThat(RutValidator.formatear("123456785")).isEqualTo("12.345.678-5");
    }

    @Test
    @DisplayName("formatear: entrada ya formateada se normaliza al mismo resultado")
    void formatear_yaFormateado_idempotente() {
        assertThat(RutValidator.formatear("12.345.678-5")).isEqualTo("12.345.678-5");
    }

    @Test
    @DisplayName("formatear: DV 'K' se pasa a mayúscula")
    void formatear_dvK_mayuscula() {
        assertThat(RutValidator.formatear("6-k")).isEqualTo("6-K");
    }

    @Test
    @DisplayName("formatear: cuerpo corto sin separador de miles")
    void formatear_cuerpoCorto() {
        assertThat(RutValidator.formatear("31-0")).isEqualTo("31-0");
    }
}
