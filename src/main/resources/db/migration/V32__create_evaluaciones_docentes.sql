-- V32: calificación docente por asignatura y período académico.
CREATE TABLE IF NOT EXISTS public.evaluaciones_docentes (
    id BIGSERIAL PRIMARY KEY,
    docente_id BIGINT NOT NULL,
    code_course VARCHAR(50) NOT NULL,
    periodo_id BIGINT NOT NULL,
    calificacion NUMERIC(4,2) NOT NULL CHECK (calificacion >= 0 AND calificacion <= 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evaluacion_docente FOREIGN KEY (docente_id)
        REFERENCES public.docentes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_evaluacion_asignatura FOREIGN KEY (code_course)
        REFERENCES public.asignaturas(code_course) ON DELETE RESTRICT,
    CONSTRAINT fk_evaluacion_periodo FOREIGN KEY (periodo_id)
        REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    CONSTRAINT uq_evaluacion_docente_asignatura_periodo
        UNIQUE (docente_id, code_course, periodo_id)
);

CREATE INDEX IF NOT EXISTS idx_evaluaciones_docentes_consulta
    ON public.evaluaciones_docentes(periodo_id, code_course, docente_id);

-- Migra la calificación histórica que estaba en docentes al último período
-- en el que el docente dictó cada asignatura.
INSERT INTO public.evaluaciones_docentes (docente_id, code_course, periodo_id, calificacion)
SELECT DISTINCT ON (da.docente_id, da.code_course, da.periodo_id)
    da.docente_id, da.code_course, da.periodo_id, d.evaluacion_valor
FROM public.docente_asignatura da
JOIN public.docentes d ON d.id = da.docente_id
WHERE d.evaluacion_valor IS NOT NULL
  AND da.periodo_id IS NOT NULL
ON CONFLICT (docente_id, code_course, periodo_id) DO NOTHING;
