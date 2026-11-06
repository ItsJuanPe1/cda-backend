-- ==============================================================================
-- V27: HISTÓRICO DOCENTE-ASIGNATURA-PROGRAMA (HU-049)
-- ==============================================================================
-- Guarda las asignaturas que un docente ha dictado en un programa. No se borra
-- físicamente (RN-13): quitar una asociación solo la desactiva.
-- El histórico se alimenta de tres formas:
--   1. Asociación individual del administrador (MANUAL).
--   2. Carga inicial del histórico (CARGA_INICIAL).
--   3. Asignación de un docente a un curso de un periodo (PROGRAMACION), tanto
--      desde el servicio Java como por el trigger de esta migración.

CREATE TABLE IF NOT EXISTS public.docente_asignatura (
    id BIGSERIAL PRIMARY KEY,
    docente_id BIGINT NOT NULL,
    code_course VARCHAR(50) NOT NULL,
    code_program VARCHAR(50) NOT NULL,
    periodo_id BIGINT,
    origen VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    desactivado_at TIMESTAMPTZ,

    CONSTRAINT fk_docente_asignatura_docente FOREIGN KEY (docente_id)
        REFERENCES public.docentes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_docente_asignatura_asignatura FOREIGN KEY (code_course)
        REFERENCES public.asignaturas(code_course) ON DELETE RESTRICT,
    CONSTRAINT fk_docente_asignatura_programa FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE RESTRICT,
    CONSTRAINT fk_docente_asignatura_periodo FOREIGN KEY (periodo_id)
        REFERENCES public.periodos_academicos(id) ON DELETE SET NULL,

    CONSTRAINT chk_docente_asignatura_origen
        CHECK (origen IN ('MANUAL', 'CARGA_INICIAL', 'PROGRAMACION')),
    CONSTRAINT uq_docente_asignatura_programa
        UNIQUE (docente_id, code_course, code_program)
);

CREATE INDEX IF NOT EXISTS idx_docente_asignatura_docente
    ON public.docente_asignatura(docente_id);
CREATE INDEX IF NOT EXISTS idx_docente_asignatura_asignatura
    ON public.docente_asignatura(code_course);
CREATE INDEX IF NOT EXISTS idx_docente_asignatura_programa
    ON public.docente_asignatura(code_program);
CREATE INDEX IF NOT EXISTS idx_docente_asignatura_activo
    ON public.docente_asignatura(activo);

-- Criterio 4: cuando un docente dicta una asignatura en un periodo, el histórico
-- se actualiza automáticamente. Se hace en la base para cubrir cualquier vía de
-- escritura (servicio, carga masiva o SQL directo) sin depender del frontend.
CREATE OR REPLACE FUNCTION public.fn_curso_docente_historico()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id_docente IS NOT NULL THEN
        INSERT INTO public.docente_asignatura (
            docente_id, code_course, code_program, periodo_id, origen, activo, updated_at
        ) VALUES (
            NEW.id_docente, NEW.code_course, NEW.code_program, NEW.periodo_id,
            'PROGRAMACION', TRUE, CURRENT_TIMESTAMP
        )
        ON CONFLICT (docente_id, code_course, code_program) DO UPDATE
        SET activo = TRUE,
            desactivado_at = NULL,
            periodo_id = EXCLUDED.periodo_id,
            updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_curso_docente_historico ON public.cursos_programacion;
CREATE TRIGGER trg_curso_docente_historico
    AFTER INSERT OR UPDATE OF id_docente ON public.cursos_programacion
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_curso_docente_historico();

-- Backfill: los cursos ya programados con docente forman parte del histórico.
INSERT INTO public.docente_asignatura (
    docente_id, code_course, code_program, periodo_id, origen, activo, updated_at
)
SELECT DISTINCT ON (c.id_docente, c.code_course, c.code_program)
    c.id_docente, c.code_course, c.code_program, c.periodo_id,
    'PROGRAMACION', TRUE, CURRENT_TIMESTAMP
FROM public.cursos_programacion c
WHERE c.id_docente IS NOT NULL
ORDER BY c.id_docente, c.code_course, c.code_program, c.periodo_id DESC
ON CONFLICT (docente_id, code_course, code_program) DO NOTHING;
