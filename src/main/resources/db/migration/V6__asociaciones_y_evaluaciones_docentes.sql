-- =============================================================================
-- V6: Asociaciones docente-asignatura (histórico + manual) y evaluaciones docentes
-- Tablas: docente_asignatura, evaluaciones_docentes
-- Trigger: trg_curso_docente_historico -> fn_curso_docente_historico()
-- =============================================================================

-- 1. Asociaciones docente <-> asignatura por facultad
CREATE TABLE IF NOT EXISTS public.docente_asignatura (
    id             BIGSERIAL   PRIMARY KEY,
    docente_id     BIGINT      NOT NULL REFERENCES public.docentes(id) ON DELETE RESTRICT,
    code_course    VARCHAR(50) NOT NULL REFERENCES public.asignaturas(code_course) ON DELETE RESTRICT,
    id_facultad    INTEGER     NOT NULL REFERENCES public."Facultad"(id) ON DELETE RESTRICT,
    periodo_id     BIGINT      REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    origen         VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    activo         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    desactivado_at TIMESTAMPTZ,
    CONSTRAINT uq_docente_asignatura_facultad UNIQUE (docente_id, code_course, id_facultad),
    CONSTRAINT chk_docente_asignatura_origen  CHECK (origen IN ('MANUAL', 'PROGRAMACION'))
);

CREATE INDEX IF NOT EXISTS idx_docente_asignatura_docente    ON public.docente_asignatura (docente_id);
CREATE INDEX IF NOT EXISTS idx_docente_asignatura_asignatura ON public.docente_asignatura (code_course);
CREATE INDEX IF NOT EXISTS idx_docente_asignatura_facultad   ON public.docente_asignatura (id_facultad);

-- Sincronización automática del histórico cuando se asigna un docente a un curso programado
CREATE OR REPLACE FUNCTION public.fn_curso_docente_historico()
RETURNS TRIGGER AS $$
DECLARE
    v_facultad INTEGER;
BEGIN
    IF NEW.id_docente IS NOT NULL THEN
        SELECT COALESCE(p.id_facultad_programa, ua.id_facultad)
        INTO v_facultad
        FROM public.programas p
        JOIN public.asignaturas a ON a.code_course = NEW.code_course
        LEFT JOIN public.unidades_academicas ua ON ua.codigo = a.codigo_unidad_academica
        WHERE p.code_program = NEW.code_program;

        IF v_facultad IS NOT NULL THEN
            INSERT INTO public.docente_asignatura (
                docente_id, code_course, id_facultad, periodo_id, origen, activo, updated_at
            ) VALUES (
                NEW.id_docente, NEW.code_course, v_facultad, NEW.periodo_id,
                'PROGRAMACION', TRUE, CURRENT_TIMESTAMP
            )
            ON CONFLICT (docente_id, code_course, id_facultad) DO UPDATE
            SET activo = TRUE,
                desactivado_at = NULL,
                periodo_id = EXCLUDED.periodo_id,
                updated_at = CURRENT_TIMESTAMP;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_curso_docente_historico ON public.cursos_programacion;
CREATE TRIGGER trg_curso_docente_historico
    AFTER INSERT OR UPDATE OF id_docente ON public.cursos_programacion
    FOR EACH ROW EXECUTE FUNCTION public.fn_curso_docente_historico();

-- 2. Evaluaciones de desempeño docente por asignatura y período
CREATE TABLE IF NOT EXISTS public.evaluaciones_docentes (
    id           BIGSERIAL     PRIMARY KEY,
    docente_id   BIGINT        NOT NULL REFERENCES public.docentes(id) ON DELETE RESTRICT,
    code_course  VARCHAR(50)   NOT NULL REFERENCES public.asignaturas(code_course) ON DELETE RESTRICT,
    periodo_id   BIGINT        NOT NULL REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    calificacion NUMERIC(4, 2) NOT NULL CHECK (calificacion >= 0 AND calificacion <= 5),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_evaluacion_docente_asignatura_periodo
        UNIQUE (docente_id, code_course, periodo_id)
);

CREATE INDEX IF NOT EXISTS idx_evaluaciones_periodo_asignatura
    ON public.evaluaciones_docentes (periodo_id, code_course);

CREATE INDEX IF NOT EXISTS idx_evaluaciones_docente_periodo
    ON public.evaluaciones_docentes (docente_id, periodo_id);
