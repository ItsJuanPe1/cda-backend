-- V47: Elimina la columna redundante usuarios.activo.
-- El estado activo/inactivo de cada perfil vive en docentes.estado y coordinadores.activo.
ALTER TABLE usuarios DROP COLUMN IF EXISTS activo;
