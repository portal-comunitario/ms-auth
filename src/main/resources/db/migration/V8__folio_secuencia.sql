-- ============================================================================
-- V8: Folio de certificado por SECUENCIA (monotónica, nunca se reutiliza,
--   aunque se eliminen certificados). Se inicializa por sobre el máximo actual.
-- ============================================================================
CREATE SEQUENCE IF NOT EXISTS certificado_folio_seq;

SELECT setval(
    'certificado_folio_seq',
    COALESCE(
        (SELECT MAX(CAST(split_part(folio, '-', 3) AS INTEGER))
         FROM solicitudes_certificado
         WHERE folio ~ '^VLF-[0-9]+-[0-9]+$'),
        0
    ) + 1,
    false
);
