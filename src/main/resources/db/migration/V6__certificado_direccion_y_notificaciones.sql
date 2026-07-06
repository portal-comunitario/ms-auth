-- ============================================================================
-- V6: La dirección y fecha de residencia se capturan en la solicitud de
--   certificado (se usan en el PDF y, al aprobar, actualizan el perfil).
--   Además, preferencia de notificaciones del vecino.
-- ============================================================================
ALTER TABLE solicitudes_certificado ADD COLUMN IF NOT EXISTS direccion VARCHAR(300);
ALTER TABLE solicitudes_certificado ADD COLUMN IF NOT EXISTS inicio_residencia DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notificaciones_activas BOOLEAN NOT NULL DEFAULT FALSE;
