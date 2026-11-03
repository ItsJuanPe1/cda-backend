package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.service.ProgramacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/programacion")
@RequiredArgsConstructor
@Tag(name = "Programación Académica", description = "Endpoints para la generación y consulta de cursos de un período")
public class ProgramacionController {

    private final ProgramacionService programacionService;

    @PostMapping("/generar")
    @Operation(summary = "Generar la programación académica a partir de cohortes confirmadas y mallas")
    public ResponseEntity<ProgramacionDto.ResumenResponse> generarProgramacion(
            @RequestParam("periodoId") Long periodoId,
            @RequestBody(required = false) ProgramacionDto.GenerarRequest request) {
        boolean sobrescribir = request != null && request.isSobrescribir();
        return ResponseEntity.ok(programacionService.generarProgramacion(periodoId, sobrescribir));
    }

    @GetMapping
    @Operation(summary = "Consultar cursos programados de un período con filtros opcionales")
    public ResponseEntity<List<ProgramacionDto.CursoResponse>> obtenerProgramacion(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam(value = "codeProgram", required = false) String codeProgram,
            @RequestParam(value = "codeCourse", required = false) String codeCourse,
            @RequestParam(value = "jornada", required = false) Jornada jornada) {
        return ResponseEntity.ok(programacionService.obtenerProgramacion(periodoId, codeProgram, codeCourse, jornada));
    }

    @GetMapping("/resumen")
    @Operation(summary = "Consultar el resumen consolidado de cursos programados")
    public ResponseEntity<ProgramacionDto.ResumenResponse> obtenerResumen(@RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(programacionService.obtenerResumen(periodoId));
    }
}
