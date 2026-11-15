package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.service.ProgramacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/programacion")
@Tag(name = "Programacion Academica", description = "Endpoints para la generacion y consulta de cursos de un periodo")
public class ProgramacionController {

    private final ProgramacionService programacionService;

    @Autowired
    public ProgramacionController(ProgramacionService programacionService) {
        this.programacionService = programacionService;
    }

    @PostMapping("/generar")
    @Operation(summary = "Generar la programacion academica a partir de cohortes confirmadas y mallas")
    public ResponseEntity<ProgramacionDto.ResumenResponse> generarProgramacion(
            @RequestParam("periodoId") Long periodoId,
            @RequestBody(required = false) ProgramacionDto.GenerarRequest request) {
        boolean sobrescribir = request != null && request.isSobrescribir();
        return ResponseEntity.ok(programacionService.generarProgramacion(periodoId, sobrescribir));
    }

    @GetMapping
    @Operation(summary = "Consultar cursos programados de un periodo con filtros opcionales")
    public ResponseEntity<List<ProgramacionDto.CursoResponse>> obtenerProgramacion(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam(value = "codeProgram", required = false) String codeProgram,
            @RequestParam(value = "codeCourse", required = false) String codeCourse,
            @RequestParam(value = "jornada", required = false) Jornada jornada) {
        return ResponseEntity.ok(programacionService.obtenerProgramacion(periodoId, codeProgram, codeCourse, jornada));
    }

    @GetMapping("/cupos/config")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Consultar maximo de cupos y si el plazo de edicion esta abierto")
    public ResponseEntity<ProgramacionDto.CuposConfigResponse> obtenerConfiguracionCupos(
            @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(programacionService.obtenerConfiguracionCupos(periodoId));
    }

    @PatchMapping("/cupos")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Registrar los cupos de estudiantes de los cursos activos")
    public ResponseEntity<ProgramacionDto.ActualizarCuposResponse> actualizarCupos(
            @RequestBody(required = false) ProgramacionDto.ActualizarCuposRequest request) {
        return ResponseEntity.ok(programacionService.actualizarCupos(request));
    }

    @GetMapping("/resumen")
    @Operation(summary = "Consultar el resumen consolidado de cursos programados")
    public ResponseEntity<ProgramacionDto.ResumenResponse> obtenerResumen(@RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(programacionService.obtenerResumen(periodoId));
    }

    // HU-047: ajustes del administrador. No hay endpoint para crear cursos (criterio 3).

    @GetMapping("/cursos/{id}/exclusion/vista-previa")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Ver como quedarian los grupos de la asignatura si se excluye el curso")
    public ResponseEntity<ProgramacionDto.AjusteGruposResponse> vistaPreviaExclusion(@PathVariable("id") Long id) {
        return ResponseEntity.ok(programacionService.vistaPreviaExclusion(id));
    }

    @PatchMapping("/cursos/{id}/excluir")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Excluir un curso con justificacion y renumerar los grupos de la asignatura")
    public ResponseEntity<ProgramacionDto.AjusteGruposResponse> excluir(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ProgramacionDto.JustificacionRequest request) {
        return ResponseEntity.ok(programacionService.excluir(id, request));
    }

    @PatchMapping("/cursos/{id}/reactivar")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Reactivar un curso excluido; toma el siguiente grupo de la serie")
    public ResponseEntity<ProgramacionDto.AjusteGruposResponse> reactivar(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ProgramacionDto.JustificacionRequest request) {
        return ResponseEntity.ok(programacionService.reactivar(id, request));
    }

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
