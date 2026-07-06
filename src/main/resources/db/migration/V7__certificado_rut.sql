-- V7: el RUT también se captura en la solicitud de certificado (se compara con la cédula
--   y, al aprobar, actualiza el perfil).
ALTER TABLE solicitudes_certificado ADD COLUMN IF NOT EXISTS rut VARCHAR(20);
