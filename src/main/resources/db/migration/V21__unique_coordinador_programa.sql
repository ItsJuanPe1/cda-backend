-- Un programa puede tener como máximo un coordinador asignado. PostgreSQL permite
-- múltiples NULL, necesarios para coordinadores que todavía no tienen programa.
-- Si existen duplicados anteriores, se detiene la migración sin elegir arbitrariamente
-- qué coordinador conservar; consulta el mensaje y corrige esos registros manualmente.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.coordinadores
        WHERE code_program IS NOT NULL
        GROUP BY code_program
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'No se puede crear el índice único de coordinadores: hay valores code_program duplicados. Ejecuta: SELECT code_program, COUNT(*) FROM public.coordinadores WHERE code_program IS NOT NULL GROUP BY code_program HAVING COUNT(*) > 1; y corrige las asignaciones antes de reiniciar.';
END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_coordinadores_code_program_not_null
    ON public.coordinadores (code_program)
    WHERE code_program IS NOT NULL;
