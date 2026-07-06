-- ============================================================================
-- V5: Certificados de residencia.
--   Solicitud del vecino (con documentos: cédula + comprobante) → aprobación
--   del dirigente → emisión del PDF. Archivos guardados como BYTEA.
-- ============================================================================
CREATE TABLE IF NOT EXISTS solicitudes_certificado (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vecino_id UUID NOT NULL,
    vecino_email VARCHAR(255) NOT NULL,
    vecino_nombre VARCHAR(255),
    motivo VARCHAR(500),
    estado VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO',
    folio VARCHAR(40),
    motivo_rechazo VARCHAR(500),
    fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion TIMESTAMP
);

CREATE TABLE IF NOT EXISTS certificado_archivos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitud_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,          -- CEDULA / COMPROBANTE / PDF
    content_type VARCHAR(120),
    filename VARCHAR(255),
    data BYTEA NOT NULL,
    CONSTRAINT uq_archivo UNIQUE (solicitud_id, tipo)
);
