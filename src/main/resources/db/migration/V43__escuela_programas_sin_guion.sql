-- Corrige la asignación de escuelas a programas: V37 solo reconocía códigos con guion (2724-DIU) y los
-- programas cuyo código es solo el número base (2724) quedaban sin escuela, por lo que veían todas las
-- electivas de la facultad. Es idempotente: no duplica lo que ya existe.
INSERT INTO public.programa_escuelas (code_program, codigo_escuela, propia)
SELECT p.code_program, v.escuela, v.propia
FROM public.programas p
JOIN (VALUES
    ('2724', '750', true),   -- Tecnología en Desarrollo de Software
    ('2725', '710', true),   -- Tecnología en Electrónica Industrial
    ('2728', '730', true),   -- Tecnología en Producción Agroambiental
    ('2726', '745', true),   -- Tecnología de Procesamiento de Alimentos
    ('3751', '760', true),   -- Ingeniería Industrial
    ('2722', '780', true),   -- Tecnología en Mantenimiento de Sistemas Electromecánicos
    ('2135', '730', true),   -- Manejo de Producción Agroforestal
    ('3846', '801', true),   -- Administración de Empresas
    ('3846', '802', false),
    ('2839', '801', true),   -- Tecnología en Gestión Logística
    ('2839', '802', false),
    ('3841', '802', true),   -- Contaduría Pública
    ('3841', '801', false)
) AS v(base, escuela, propia) ON (p.code_program = v.base OR p.code_program LIKE v.base || '-%')
ON CONFLICT (code_program, codigo_escuela) DO NOTHING;