-- Carga académica docente: horas semanales de cada asignatura (las llena el admin al editar).
-- Una asignatura sin horas no se puede asignar a un docente.
ALTER TABLE asignaturas
    ADD COLUMN horas_semanales SMALLINT
    CONSTRAINT ck_asignaturas_horas CHECK (horas_semanales IS NULL OR horas_semanales BETWEEN 1 AND 20);

-- La carga se calcula sumando los cursos de un docente en un período.
CREATE INDEX IF NOT EXISTS ix_cursos_docente_periodo
    ON cursos_programacion (id_docente, periodo_id);