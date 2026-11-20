-- usuarios.telefono (entero) se reemplaza por usuarios.celular (texto de 10 dígitos): un entero no puede
-- guardar 3001234567. Los valores antiguos no eran celulares válidos, así que no se conservan.
-- usuarios.email_personal pasa a ser opcional, igual que en docentes y coordinadores.
ALTER TABLE public.usuarios RENAME COLUMN telefono TO celular;
ALTER TABLE public.usuarios
    ALTER COLUMN celular TYPE VARCHAR(10) USING NULL;
ALTER TABLE public.usuarios
    ALTER COLUMN email_personal DROP NOT NULL;
