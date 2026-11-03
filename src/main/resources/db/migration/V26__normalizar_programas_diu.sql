-- ==============================================================================
-- V26: Normalizar programas diurnos con sufijo -DIU (y nocturnos con -NOC)
-- ==============================================================================

ALTER TABLE public.programas DROP CONSTRAINT IF EXISTS chk_programas_codigo;

DO $$
DECLARE
    r RECORD;
    v_target_code VARCHAR(50);
BEGIN
    FOR r IN
        SELECT *
        FROM public.programas
        WHERE code_program !~ '-(DIU|NOC)$'
        ORDER BY code_program
    LOOP
        IF UPPER(COALESCE(r.jornada, 'DIURNA')) = 'NOCTURNA' THEN
            v_target_code := r.code_program || '-NOC';
        ELSE
            v_target_code := r.code_program || '-DIU';
        END IF;

        -- Si aún no existe la instancia con el sufijo (-DIU o -NOC), se crea copiando los datos
        IF NOT EXISTS (SELECT 1 FROM public.programas WHERE code_program = v_target_code) THEN
            INSERT INTO public.programas (
                code_program, name_program, numero_semestre, id_facultad_programa,
                jornada, semestres_nocturna, ingreso, estado, apertura, numero_resolucion
            ) VALUES (
                v_target_code, r.name_program, r.numero_semestre, r.id_facultad_programa,
                r.jornada, r.semestres_nocturna, r.ingreso, r.estado, r.apertura, r.numero_resolucion
            );
        END IF;

        -- Actualizar referencias en pensum_academico hacia v_target_code
        UPDATE public.pensum_academico p
        SET code_program = v_target_code
        WHERE p.code_program = r.code_program
          AND NOT EXISTS (
              SELECT 1 FROM public.pensum_academico p2
              WHERE p2.code_program = v_target_code
                AND p2.code_course = p.code_course
                AND p2.version = p.version
          );

        DELETE FROM public.pensum_academico WHERE code_program = r.code_program;

        -- Actualizar referencias en coordinadores hacia v_target_code
        UPDATE public.coordinadores c
        SET code_program = v_target_code
        WHERE c.code_program = r.code_program
          AND NOT EXISTS (
              SELECT 1 FROM public.coordinadores c2
              WHERE c2.code_program = v_target_code
          );

        UPDATE public.coordinadores
        SET code_program = NULL
        WHERE code_program = r.code_program;

        -- Actualizar referencias en docentes hacia v_target_code
        UPDATE public.docentes
        SET code_program = v_target_code
        WHERE code_program = r.code_program;

        -- Actualizar referencias en cohortes_periodo (V24) si la tabla existe
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'cohortes_periodo'
        ) THEN
            UPDATE public.cohortes_periodo
            SET code_program = v_target_code
            WHERE code_program = r.code_program;
        END IF;

        -- Actualizar referencias en cursos_programacion (V25) si la tabla existe
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'cursos_programacion'
        ) THEN
            UPDATE public.cursos_programacion
            SET code_program = v_target_code
            WHERE code_program = r.code_program;
        END IF;

        -- Eliminar el registro antiguo sin sufijo
        DELETE FROM public.programas WHERE code_program = r.code_program;
    END LOOP;
END $$;

