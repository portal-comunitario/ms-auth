-- Acceso al sitio: las cuentas nuevas quedan "en revisión" (solo lectura) hasta que un dirigente las apruebe.
ALTER TABLE users ADD COLUMN IF NOT EXISTS acceso_aprobado BOOLEAN NOT NULL DEFAULT FALSE;
-- Los usuarios ya existentes quedan aprobados (ya tenían acceso).
UPDATE users SET acceso_aprobado = TRUE;
