-- ==============================================================================
-- HU-015: CORREO PERSONAL (CONTACTO) Y FOTO DEL DOCENTE
-- ==============================================================================

-- Correo personal: dato de contacto opcional que edita el docente desde "Mi perfil".
-- El formato y que no sea institucional se validan en la capa de servicio.
ALTER TABLE public.docentes
    ADD COLUMN IF NOT EXISTS correo_personal VARCHAR(100);

-- Foto del docente: una sola fila por docente (UNIQUE docente_id).
-- Al cambiar la foto se reemplaza el contenido de la fila; al eliminarla se desactiva
-- (activa = false, RN-13). Solo el administrador la borra directamente en la base.
CREATE TABLE IF NOT EXISTS public.docente_fotos (
    id           BIGSERIAL    PRIMARY KEY,
    docente_id   BIGINT       NOT NULL UNIQUE,
    contenido    BYTEA        NOT NULL,
    tipo_mime    VARCHAR(20)  NOT NULL,
    tamano_bytes INTEGER      NOT NULL,
    activa       BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_docente_fotos_docente FOREIGN KEY (docente_id)
        REFERENCES public.docentes(id) ON DELETE RESTRICT,

    CONSTRAINT chk_docente_fotos_tipo
        CHECK (tipo_mime IN ('image/jpeg', 'image/png')),

    -- Máximo 5 MB (5 * 1024 * 1024 bytes)
    CONSTRAINT chk_docente_fotos_tamano
        CHECK (tamano_bytes > 0 AND tamano_bytes <= 5242880)
);
