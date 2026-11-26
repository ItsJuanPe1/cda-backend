-- =============================================================================
-- V4: Programación académica (cursos obligatorios, electivas, horarios y exclusiones)
-- Tabla: cursos_programacion
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.cursos_programacion (
    id                      BIGSERIAL    PRIMARY KEY,
    periodo_id              BIGINT       NOT NULL REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    code_program            VARCHAR(50)  NOT NULL REFERENCES public.programas(code_program) ON DELETE RESTRICT,
    code_course             VARCHAR(50)  NOT NULL REFERENCES public.asignaturas(code_course) ON DELETE RESTRICT,
    semestre                INTEGER      NOT NULL,
    jornada                 VARCHAR(10)  NOT NULL DEFAULT 'DIURNA' CHECK (jornada IN ('DIURNA', 'NOCTURNA')),
    numero_resolucion       VARCHAR(50)  NOT NULL,
    grupo                   INTEGER,
    cupos                   INTEGER,
    code_course_espacio     VARCHAR(50),
    credits                 INTEGER      NOT NULL DEFAULT 0,
    nombre_asignatura       VARCHAR(150) NOT NULL,
    id_docente              BIGINT       REFERENCES public.docentes(id) ON DELETE RESTRICT,
    dia                     INTEGER,
    hora_inicio             TIME,
    hora_fin                TIME,
    dia2                    INTEGER,
    hora_inicio2            TIME,
    hora_fin2               TIME,
    horario                 VARCHAR(150),
    estado                  VARCHAR(20)  NOT NULL DEFAULT 'PROGRAMADO' CHECK (estado IN ('PROGRAMADO', 'ASIGNADO', 'EXCLUIDO')),
    justificacion_exclusion VARCHAR(500),
    excluido_por            VARCHAR(150),
    fecha_exclusion         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_curso_cupos_positivos CHECK (cupos IS NULL OR cupos > 0),
    CONSTRAINT ck_curso_excluido CHECK (
        estado <> 'EXCLUIDO' OR (
            grupo IS NULL
            AND justificacion_exclusion IS NOT NULL
            AND excluido_por IS NOT NULL
            AND fecha_exclusion IS NOT NULL
        )
    ),
    -- Deferrable para permitir renumerar grupos consecutivos (50, 51, ...) dentro de una transacción
    CONSTRAINT uq_curso_periodo_asignatura_grupo
        UNIQUE (periodo_id, code_course, grupo) DEFERRABLE INITIALLY DEFERRED
);

-- Un espacio de electiva de la malla (ELE-PROF-*) solo puede ocuparse una vez por programa y período;
-- las complementarias propuestas ('COMPLEMENTARIA') pueden ser varias por programa.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cursos_programacion_espacio
    ON public.cursos_programacion (periodo_id, code_program, code_course_espacio)
    WHERE code_course_espacio IS NOT NULL
      AND code_course_espacio <> 'COMPLEMENTARIA'
      AND estado <> 'EXCLUIDO';

CREATE INDEX IF NOT EXISTS ix_cursos_periodo_programa   ON public.cursos_programacion (periodo_id, code_program);
CREATE INDEX IF NOT EXISTS ix_cursos_periodo_asignatura ON public.cursos_programacion (periodo_id, code_course);
CREATE INDEX IF NOT EXISTS ix_cursos_docente_periodo    ON public.cursos_programacion (id_docente, periodo_id);
