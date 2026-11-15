-- HU-060: Horario asignado a cursos_programacion según disponibilidad del docente
ALTER TABLE public.cursos_programacion
    ADD COLUMN IF NOT EXISTS dia SMALLINT,
    ADD COLUMN IF NOT EXISTS hora_inicio TIME,
    ADD COLUMN IF NOT EXISTS hora_fin TIME,
    ADD COLUMN IF NOT EXISTS horario VARCHAR(100);

COMMENT ON COLUMN public.cursos_programacion.dia IS 'Día de la semana asignado (1=Lunes, ..., 7=Domingo)';
COMMENT ON COLUMN public.cursos_programacion.hora_inicio IS 'Hora de inicio de la clase';
COMMENT ON COLUMN public.cursos_programacion.hora_fin IS 'Hora de finalización de la clase';
COMMENT ON COLUMN public.cursos_programacion.horario IS 'Texto descriptivo del horario asignado (ej: Lunes 07:00 - 10:00)';
