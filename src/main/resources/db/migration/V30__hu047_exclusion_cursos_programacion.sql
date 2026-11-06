-- HU-047: auditoría de exclusión de cursos y grupo único por asignatura
ALTER TABLE cursos_programacion
    ADD COLUMN justificacion_exclusion VARCHAR(500),
    ADD COLUMN excluido_por            VARCHAR(150),
    ADD COLUMN fecha_exclusion         TIMESTAMPTZ;

-- Un curso excluido no tiene grupo y siempre tiene justificación.
ALTER TABLE cursos_programacion
    ADD CONSTRAINT ck_curso_excluido
    CHECK (estado <> 'EXCLUIDO'
           OR (grupo IS NULL AND justificacion_exclusion IS NOT NULL
               AND excluido_por IS NOT NULL AND fecha_exclusion IS NOT NULL));

-- Grupo único por asignatura en el periodo. Se verifica al final de la transacción
-- para permitir la renumeración (52 → 51) sin choques intermedios. Los NULL no chocan.
ALTER TABLE cursos_programacion
    ADD CONSTRAINT uq_curso_periodo_asignatura_grupo
    UNIQUE (periodo_id, code_course, grupo) DEFERRABLE INITIALLY DEFERRED;
