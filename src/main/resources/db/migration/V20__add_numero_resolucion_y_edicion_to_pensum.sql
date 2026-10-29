-- V20: Agrega número de resolución, número de semestres y registro de edición a las mallas (pensum_academico) y programas.
ALTER TABLE public.pensum_academico
    ADD COLUMN IF NOT EXISTS numero_resolucion VARCHAR(50),
    ADD COLUMN IF NOT EXISTS numero_semestres INT,
    ADD COLUMN IF NOT EXISTS editada BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS fecha_edicion TIMESTAMP;

ALTER TABLE public.programas
    ADD COLUMN IF NOT EXISTS numero_resolucion VARCHAR(50);

