-- HU-015: número de celular del docente, distinto del teléfono (decisión del 2026-11-10).
-- Opcional (NULL), solo dígitos, de 7 a 10. Se guarda como texto para conservar ceros iniciales.
-- Lo edita el propio docente desde «Mi perfil» (RN-08). No transforma ni borra datos:
-- las filas existentes quedan con celular = NULL.
ALTER TABLE docentes
    ADD COLUMN celular VARCHAR(10);

ALTER TABLE docentes
    ADD CONSTRAINT chk_docentes_celular_digitos
    CHECK (celular IS NULL OR celular ~ '^[0-9]{7,10}$');

COMMENT ON COLUMN docentes.celular IS 'HU-015: celular del docente, opcional, de 7 a 10 dígitos';
