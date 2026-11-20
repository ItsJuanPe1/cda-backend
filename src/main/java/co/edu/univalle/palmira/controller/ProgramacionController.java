package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.service.ProgramacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

/**
 * Controlador REST para la gestión de la programación académica.
 * <p>
 * Proporciona endpoints para la generación automática de cursos, consulta con filtros,
 * exclusión y reactivación justificada de cursos, configuración y actualización de cupos,
 * y asignación horaria de docentes con soporte para 1 día continuo o 2 días divididos.
 * </p>
 */
@RestController
@RequestMapping("/api/programacion")
@Tag(name = "Programacion Academica", description = "Endpoints para la generacion y consulta de cursos de un periodo")
public class ProgramacionController {

    private final ProgramacionService programacionService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param programacionService servicio de lógica de programación académica
     */
    @Autowired
    public ProgramacionController(ProgramacionService programacionService) {
        this.programacionService = programacionService;
    }

    /**
     * Genera la programación académica de un período a partir de cohortes confirmadas y mallas curriculares.
     *
     * @param periodoId identificador del período académico
     * @param request   parámetros de generación (opción de sobrescribir)
     * @return resumen con cantidad de cursos generados y métricas
     */
    @PostMapping("/generar")
    @Operation(summary = "Generar la programacion academica a partir de cohortes confirmadas y mallas")
    public ResponseEntity<ProgramacionDto.ResumenResponse> generarProgramacion(
            @RequestParam("periodoId") Long periodoId,
            @RequestBody(required = false) ProgramacionDto.GenerarRequest request) {
        boolean sobrescribir = request != null && request.isSobrescribir();
        return ResponseEntity.ok(programacionService.generarProgramacion(periodoId, sobrescribir));
    }

    /**
     * Consulta los cursos programados para un período académico con filtros opcionales.
     *
     * @param periodoId   identificador del período académico
     * @param codeProgram código opcional del programa
     * @param codeCourse  código opcional de la asignatura
     * @param jornada     jornada académica opcional (DIURNA, NOCTURNA)
     * @return lista de cursos programados correspondientes
     */
    @GetMapping
    @Operation(summary = "Consultar cursos programados de un periodo con filtros opcionales")
    public ResponseEntity<List<ProgramacionDto.CursoResponse>> obtenerProgramacion(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam(value = "codeProgram", required = false) String codeProgram,
            @RequestParam(value = "codeCourse", required = false) String codeCourse,
            @RequestParam(value = "jornada", required = false) Jornada jornada) {
        return ResponseEntity.ok(programacionService.obtenerProgramacion(periodoId, codeProgram, codeCourse, jornada));
    }

    /**
     * Obtiene los parámetros de configuración de cupos y verifica si el plazo de edición se encuentra abierto.
     *
     * @param periodoId identificador del período
     * @return configuración con cupo máximo permitido y estado de vigencia del plazo
     */
    @GetMapping("/cupos/config")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Consultar maximo de cupos y si el plazo de edicion esta abierto")
    public ResponseEntity<ProgramacionDto.CuposConfigResponse> obtenerConfiguracionCupos(
            @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(programacionService.obtenerConfiguracionCupos(periodoId));
    }

    /**
     * Actualiza masivamente los cupos de estudiantes para los cursos activos del período.
     *
     * @param request lista de identificadores de curso y su nueva cantidad de cupos
     * @return respuesta de confirmación de actualización
     */
    @PatchMapping("/cupos")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Registrar los cupos de estudiantes de los cursos activos")
    public ResponseEntity<ProgramacionDto.ActualizarCuposResponse> actualizarCupos(
            @RequestBody(required = false) ProgramacionDto.ActualizarCuposRequest request) {
        return ResponseEntity.ok(programacionService.actualizarCupos(request));
    }

    /**
     * Consulta el resumen consolidado de cursos programados para un período.
     *
     * @param periodoId identificador del período
     * @return resumen con totales de cursos, asignaciones y cupos
     */
    @GetMapping("/resumen")
    @Operation(summary = "Consultar el resumen consolidado de cursos programados")
    public ResponseEntity<ProgramacionDto.ResumenResponse> obtenerResumen(@RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(programacionService.obtenerResumen(periodoId));
    }

    /**
     * Exporta la programación académica oficial a un archivo Excel (.xlsx).
     *
     * @param periodoId   identificador del período académico
     * @param codeProgram código de programa opcional para filtrar
     * @return archivo Excel descargable
     */
    @GetMapping("/exportar-excel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Exportar la programación académica a un archivo Excel")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam(value = "codeProgram", required = false) String codeProgram) {
        byte[] bytes = programacionService.exportarExcel(periodoId, codeProgram);
        String filename = "programacion-academica-" + periodoId + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(bytes);
    }

    /**
     * Vista previa de la renumeración de grupos que ocurriría si se excluye un curso (HU-047).
     *
     * @param id identificador del curso
     * @return detalle de reasignación de números de grupo
     */
    @GetMapping("/cursos/{id}/exclusion/vista-previa")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Ver como quedarian los grupos de la asignatura si se excluye el curso")
    public ResponseEntity<ProgramacionDto.AjusteGruposResponse> vistaPreviaExclusion(@PathVariable("id") Long id) {
        return ResponseEntity.ok(programacionService.vistaPreviaExclusion(id));
    }

    /**
     * Excluye un curso programado con justificación obligatoria y renumera los grupos restantes.
     *
     * @param id      identificador del curso
     * @param request solicitud con el texto de justificación
     * @return resultado del ajuste de grupos
     */
    @PatchMapping("/cursos/{id}/excluir")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Excluir un curso con justificacion y renumerar los grupos de la asignatura")
    public ResponseEntity<ProgramacionDto.AjusteGruposResponse> excluir(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ProgramacionDto.JustificacionRequest request) {
        return ResponseEntity.ok(programacionService.excluir(id, request));
    }

    /**
     * Reactiva un curso previamente excluido asignándole el siguiente número de grupo disponible.
     *
     * @param id      identificador del curso
     * @param request solicitud con justificación de reactivación
     * @return resultado del ajuste de grupos
     */
    @PatchMapping("/cursos/{id}/reactivar")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Reactivar un curso excluido; toma el siguiente grupo de la serie")
    public ResponseEntity<ProgramacionDto.AjusteGruposResponse> reactivar(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ProgramacionDto.JustificacionRequest request) {
        return ResponseEntity.ok(programacionService.reactivar(id, request));
    }

    /**
     * Asigna o desasigna un docente a un curso específico por su identificador.
     * Permite especificar franja en un único día o dividida en dos días de clase.
     *
     * @param id      identificador del curso
     * @param request datos de asignación (idDocente, día(s), horas de inicio/fin y etiqueta de horario)
     * @return detalle del curso actualizado
     */
    @PatchMapping("/cursos/{id}/docente")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Designar o desasignar un docente a un curso programado")
    public ResponseEntity<ProgramacionDto.CursoResponse> asignarDocente(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ProgramacionDto.AsignarDocenteRequest request) {
        Long idDocente = request != null ? request.getIdDocente() : null;
        boolean reemplazar = request != null && Boolean.TRUE.equals(request.getReemplazarDocente());
        Integer dia = request != null ? request.getDia() : null;
        LocalTime horaInicio = request != null ? request.getHoraInicio() : null;
        LocalTime horaFin = request != null ? request.getHoraFin() : null;
        Integer dia2 = request != null ? request.getDia2() : null;
        LocalTime horaInicio2 = request != null ? request.getHoraInicio2() : null;
        LocalTime horaFin2 = request != null ? request.getHoraFin2() : null;
        String horario = request != null ? request.getHorario() : null;
        return ResponseEntity.ok(programacionService.asignarDocente(
                id, idDocente, reemplazar, dia, horaInicio, horaFin, dia2, horaInicio2, horaFin2, horario));
    }

    /**
     * Asigna un docente buscando el curso por período, programa, código de materia y semestre.
     *
     * @param request datos de la materia, período y docente a asignar
     * @return detalle del curso actualizado
     */
    @PostMapping("/asignar-docente")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Designar o desasignar un docente por materia y semestre")
    public ResponseEntity<ProgramacionDto.CursoResponse> asignarDocentePorMateria(
            @RequestBody ProgramacionDto.AsignarDocenteRequest request) {
        if (request == null || request.getPeriodoId() == null || request.getCodeProgram() == null || request.getCodeCourse() == null) {
            throw new BadRequestException("Faltan parametros obligatorios para la asignacion");
        }
        boolean reemplazar = Boolean.TRUE.equals(request.getReemplazarDocente());
        return ResponseEntity.ok(programacionService.asignarDocentePorMateria(
                request.getPeriodoId(), request.getCodeProgram(), request.getCodeCourse(), request.getSemestre(),
                request.getIdDocente(), reemplazar,
                request.getDia(), request.getHoraInicio(), request.getHoraFin(),
                request.getDia2(), request.getHoraInicio2(), request.getHoraFin2(), request.getHorario()));
    }
}
