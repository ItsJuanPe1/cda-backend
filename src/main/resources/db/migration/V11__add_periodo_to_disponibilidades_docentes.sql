ALTER TABLE public.disponibilidades_docentes
    ADD COLUMN IF NOT EXISTS periodo_id BIGINT;

ALTER TABLE public.disponibilidades_docentes
    ADD CONSTRAINT fk_disponibilidad_periodo
    FOREIGN KEY (periodo_id) REFERENCES public.periodos_academicos(id);

CREATE INDEX IF NOT EXISTS ix_disponibilidad_docente_periodo
    ON public.disponibilidades_docentes (id_docente, periodo_id, dia, hora_inicio);
