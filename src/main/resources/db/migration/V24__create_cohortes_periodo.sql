-- ==============================================================================
-- V24: CREAR TABLA DE COHORTES ACTIVAS POR PERIODO ACADEMICO (HU-042, HU-043)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.cohortes_periodo (
    id BIGSERIAL PRIMARY KEY,
    periodo_id BIGINT NOT NULL,
    code_program VARCHAR(50) NOT NULL,
    semestre INTEGER NOT NULL CHECK (semestre >= 1 AND semestre <= 20),
    jornada VARCHAR(10) NOT NULL DEFAULT 'DIURNA' CHECK (jornada IN ('DIURNA', 'NOCTURNA')),
    numero_resolucion VARCHAR(50) NOT NULL,
    confirmada BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cohortes_periodo FOREIGN KEY (periodo_id)
        REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cohortes_programa FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE RESTRICT,

    CONSTRAINT uq_cohorte_programa_periodo_semestre_jornada
        UNIQUE (periodo_id, code_program, semestre, jornada)
);

CREATE INDEX IF NOT EXISTS idx_cohortes_periodo_prog
    ON public.cohortes_periodo(periodo_id, code_program);

CREATE INDEX IF NOT EXISTS idx_cohortes_resolucion_semestre
    ON public.cohortes_periodo(code_program, numero_resolucion, semestre);
