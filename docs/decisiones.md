# Registro de Decisiones de Arquitectura y Negocio

Este documento registra las decisiones acordadas con el equipo y confirmadas por el usuario, conforme a las fuentes de verdad y orden de precedencia del proyecto.

---

## [2026-10-23] Aprobación de Contrato y Flujo de Login para Coordinador (HU-010)

* **HU afectadas:** HU-010 (Login de Coordinador), HU-001 (Login de Administrador), HU-006 (Login de Docente).
* **Decisión:**
  1. Se aprueba la especificación del contrato en `docs/contratos/HU-010.md`.
  2. El endpoint de autenticación `POST /api/auth/google` soporta el parámetro opcional `rolEsperado` (`1`: Admin, `2`: Coordinador, `3`: Docente) y auto-detección si no se especifica.
  3. Para el rol de coordinador (`rol = 2`):
     - Valida pertenencia al dominio `@correounivalle.edu.co`.
     - Si el coordinador no existe en `coordinadores`, pero el correo pertenece a otro rol (admin o docente), responde con error 403 y mensaje `"No tiene perfil de coordinador"` (Criterio 3.3).
     - Si no existe en ninguna tabla, responde con error 403 y mensaje `"Su cuenta no está registrada. Contacte al administrador"` (Criterio 2.2).
     - Si existe pero está inactivo (`activo = false`), responde con error 403 y mensaje `"Su perfil está desactivado. Contacte al administrador"` (Criterio 2.3).
     - Si está activo y no tiene programa asignado (`programa == null`), permite el inicio de sesión emitiendo el JWT con rol 2 y mensaje `"Aún no tiene un programa asignado. Contacte al administrador"` (Criterio 3.4).
     - Si está activo y tiene programa asignado, responde con rol 2, `codeProgram`, `nombrePrograma` y mensaje `"Inicio de sesión exitoso"` (Criterio 1.3).
  4. Se crea el endpoint `GET /api/coordinadores/me` protegido por Spring Security (`ROLE_COORDINADOR`, `ROLE_ADMIN`) para obtener el perfil del coordinador a partir de su JWT autenticado, eliminando la vulnerabilidad de pasar correos por query param.

