-- ==============================================================================
-- V25: CREAR TABLA DE CURSOS Y PROGRAMACION ACADEMICA (HU-045, HU-046)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.cursos_programacion (
    id BIGSERIAL PRIMARY KEY,
    periodo_id BIGINT NOT NULL,
    code_program VARCHAR(50) NOT NULL,
    code_course VARCHAR(50) NOT NULL,
    semestre INTEGER NOT NULL,
    jornada VARCHAR(10) NOT NULL DEFAULT 'DIURNA' CHECK (jornada IN ('DIURNA', 'NOCTURNA')),
    numero_resolucion VARCHAR(50) NOT NULL,
    grupo INTEGER,
    credits INTEGER NOT NULL DEFAULT 0,
    nombre_asignatura VARCHAR(150) NOT NULL,
    id_docente BIGINT,
    estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO' CHECK (estado IN ('PROGRAMADO', 'ASIGNADO', 'EXCLUIDO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cursos_periodo FOREIGN KEY (periodo_id)
        REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cursos_programa FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE RESTRICT,
    CONSTRAINT fk_cursos_asignatura FOREIGN KEY (code_course)
        REFERENCES public.asignaturas(code_course) ON DELETE RESTRICT,
    CONSTRAINT fk_cursos_docente FOREIGN KEY (id_docente)
        REFERENCES public.docentes(id) ON DELETE SET NULL,

    CONSTRAINT uq_curso_periodo_programa_materia_jornada
        UNIQUE (periodo_id, code_program, code_course, jornada)
);

CREATE INDEX IF NOT EXISTS idx_cursos_periodo_programa
    ON public.cursos_programacion(periodo_id, code_program);

CREATE INDEX IF NOT EXISTS idx_cursos_periodo_asignatura
    ON public.cursos_programacion(periodo_id, code_course);
