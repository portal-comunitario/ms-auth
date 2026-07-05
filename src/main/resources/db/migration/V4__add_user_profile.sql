-- ============================================================================
-- V4: Perfil del vecino. Datos para certificados y notificaciones WhatsApp.
--   Todos opcionales salvo estado_validacion (default PENDIENTE).
-- ============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS telefono VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS rut VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS direccion VARCHAR(300);
ALTER TABLE users ADD COLUMN IF NOT EXISTS inicio_residencia DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS estado_validacion VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
