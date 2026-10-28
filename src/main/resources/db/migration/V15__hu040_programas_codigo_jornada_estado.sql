-- HU-040: código de programa de 4 dígitos, jornada, duración nocturna, ingreso y estado.
-- RN-13: la malla ya no se borra en cascada al eliminar un programa.
-- numero_semestre se conserva como duración en jornada diurna.

-- 1. Quitar temporalmente las llaves foráneas hacia programas.
--    Nombres de la base compartida y, por si acaso, los del script V1 (base local).
ALTER TABLE public.pensum_academico DROP CONSTRAINT IF EXISTS pensum_academico_code_program_fkey;
ALTER TABLE public.pensum_academico DROP CONSTRAINT IF EXISTS fk_pensum_programa;
ALTER TABLE public.coordinadores    DROP CONSTRAINT IF EXISTS fk_coordinador_programa;
ALTER TABLE public.docentes         DROP CONSTRAINT IF EXISTS fk_docentes_programa;

-- 2. Columnas nuevas. Los valores por defecto evitan romper las ramas que
--    aún crean programas sin estos campos.
ALTER TABLE public.programas
    ADD COLUMN jornada            VARCHAR(10) NOT NULL DEFAULT 'DIURNA',
    ADD COLUMN semestres_nocturna INT,
    ADD COLUMN ingreso            VARCHAR(10) NOT NULL DEFAULT 'SEMESTRAL',
    ADD COLUMN estado             VARCHAR(10) NOT NULL DEFAULT 'ACTIVO';

-- 3. Quitar el sufijo de jornada (2135-DIU -> 2135) en las tablas hijas y en programas.
UPDATE public.pensum_academico SET code_program = left(code_program, 4) WHERE code_program ~ '^[0-9]{4}-DIU$';
UPDATE public.coordinadores    SET code_program = left(code_program, 4) WHERE code_program ~ '^[0-9]{4}-DIU$';
UPDATE public.docentes         SET code_program = left(code_program, 4) WHERE code_program ~ '^[0-9]{4}-DIU$';
UPDATE public.programas        SET code_program = left(code_program, 4) WHERE code_program ~ '^[0-9]{4}-DIU$';

-- 4. Administración de Empresas se oferta en ambas jornadas: 10 semestres en diurna y 11 en nocturna.
UPDATE public.programas
SET jornada = 'AMBAS', numero_semestre = 10, semestres_nocturna = 11
WHERE code_program = '3846';

-- 5. Reglas del programa. Si queda algún código que no sea de 4 dígitos,
--    la migración falla y no se aplica nada.
ALTER TABLE public.programas
ALTER COLUMN name_program TYPE VARCHAR(100),
    ADD CONSTRAINT chk_programas_codigo  CHECK (code_program ~ '^[0-9]{4}$'),
    ADD CONSTRAINT chk_programas_jornada CHECK (jornada IN ('DIURNA', 'NOCTURNA', 'AMBAS')),
    ADD CONSTRAINT chk_programas_ingreso CHECK (ingreso IN ('SEMESTRAL', 'ANUAL')),
    ADD CONSTRAINT chk_programas_estado  CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    ADD CONSTRAINT chk_programas_duracion_diurna CHECK (
        jornada = 'NOCTURNA' OR (numero_semestre IS NOT NULL AND numero_semestre BETWEEN 1 AND 20)),
    ADD CONSTRAINT chk_programas_duracion_nocturna CHECK (
        jornada = 'DIURNA' OR (semestres_nocturna IS NOT NULL AND semestres_nocturna BETWEEN 1 AND 20));

-- 6. Volver a crear las llaves foráneas. La malla pasa de CASCADE a RESTRICT (RN-13).
ALTER TABLE public.pensum_academico
    ADD CONSTRAINT pensum_academico_code_program_fkey FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE RESTRICT;
ALTER TABLE public.coordinadores
    ADD CONSTRAINT fk_coordinador_programa FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE SET NULL;
ALTER TABLE public.docentes
    ADD CONSTRAINT fk_docentes_programa FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE RESTRICT;