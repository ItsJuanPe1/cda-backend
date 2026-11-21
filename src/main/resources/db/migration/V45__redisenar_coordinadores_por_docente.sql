-- ==============================================================================
-- V45: REDISEÑO DE LA TABLA COORDINADORES LIGADA A DOCENTES
-- ==============================================================================
-- Los coordinadores dejan de registrarse como personas duplicadas.
-- Cada registro relaciona un docente existente con un programa académico
-- y el correo institucional de coordinación de dicho programa.
-- Un mismo docente puede coordinar múltiples programas (ej. DIU y NOC),
-- pero cada programa puede tener como máximo un coordinador activo a la vez.

DROP TABLE IF EXISTS public.coordinadores CASCADE;

CREATE TABLE public.coordinadores (
    id                   SERIAL       PRIMARY KEY,
    docente_id           BIGINT       NOT NULL REFERENCES public.docentes(id) ON DELETE RESTRICT,
    code_program         VARCHAR(50)  NOT NULL REFERENCES public.programas(code_program) ON DELETE RESTRICT,
    correo_institucional VARCHAR(150) NOT NULL,
    activo               BOOLEAN      NOT NULL DEFAULT true,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Cada programa puede tener máximo un coordinador activo a la vez
CREATE UNIQUE INDEX uq_coordinadores_programa_activo
    ON public.coordinadores (code_program)
    WHERE activo = true;

CREATE INDEX idx_coordinadores_docente ON public.coordinadores (docente_id);
CREATE INDEX idx_coordinadores_correo  ON public.coordinadores (LOWER(correo_institucional));
