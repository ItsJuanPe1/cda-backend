-- Eliminar claves foráneas antiguas que vinculen
-- id_docente con usuarios(id).
DO $$
DECLARE
fk RECORD;
BEGIN
FOR fk IN
SELECT c.conname
FROM pg_constraint c
WHERE c.conrelid =
      'public.disponibilidades_docentes'::regclass
          AND c.contype = 'f'
          AND c.confrelid = 'public.usuarios'::regclass
          AND EXISTS (
              SELECT 1
              FROM unnest(c.conkey) AS k(attnum)
              JOIN pg_attribute a
                ON a.attrelid = c.conrelid
               AND a.attnum = k.attnum
              WHERE a.attname = 'id_docente'
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE public.disponibilidades_docentes DROP CONSTRAINT %I',
            fk.conname
        );
END LOOP;
END $$;