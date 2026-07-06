package com.portalcomunitario.msauth.certificado;

/** Validación y formato de RUT chileno (módulo 11). */
public final class RutValidator {

    private RutValidator() {}

    public static boolean valido(String rut) {
        if (rut == null) return false;
        String clean = rut.replaceAll("[.\\-\\s]", "").toUpperCase();
        if (clean.length() < 2) return false;
        String cuerpo = clean.substring(0, clean.length() - 1);
        char dv = clean.charAt(clean.length() - 1);
        if (!cuerpo.matches("\\d+")) return false;
        int suma = 0, mul = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += (cuerpo.charAt(i) - '0') * mul;
            mul = (mul == 7) ? 2 : mul + 1;
        }
        int resto = 11 - (suma % 11);
        char esperado = (resto == 11) ? '0' : (resto == 10) ? 'K' : (char) ('0' + resto);
        return dv == esperado;
    }

    /** Formatea a 12.345.678-9. Asume que ya es válido. */
    public static String formatear(String rut) {
        String clean = rut.replaceAll("[.\\-\\s]", "").toUpperCase();
        String cuerpo = clean.substring(0, clean.length() - 1);
        char dv = clean.charAt(clean.length() - 1);
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            sb.insert(0, cuerpo.charAt(i));
            if (++c % 3 == 0 && i != 0) sb.insert(0, '.');
        }
        return sb + "-" + dv;
    }
}
