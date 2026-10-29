package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.PensumItemDto;
import co.edu.univalle.palmira.service.PensumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pensum")
@RequiredArgsConstructor
@Tag(name = "Pensum Académico", description = "Endpoints para la gestión de mallas curriculares")
public class PensumController {

    private final PensumService pensumService;

    @GetMapping
    @Operation(summary = "Listar todas las asignaciones de pensum")
    public ResponseEntity<List<PensumItemDto.Response>> getAll() {
        return ResponseEntity.ok(pensumService.getAll());
    }

    @GetMapping("/programa/{codeProgram}")
    @Operation(summary = "Obtener la malla curricular completa de un programa con detalles de asignaturas")
    public ResponseEntity<List<PensumItemDto.Response>> getByProgram(@PathVariable("codeProgram") String codeProgram) {
        return ResponseEntity.ok(pensumService.getByProgram(codeProgram));
    }

    @GetMapping("/programa/{codeProgram}/historial")
    public ResponseEntity<List<PensumItemDto.HistorySummary>> getHistory(
            @PathVariable("codeProgram") String codeProgram) {
        return ResponseEntity.ok(pensumService.getHistory(codeProgram));
    }

    @GetMapping("/programa/{codeProgram}/version/{version}")
    public ResponseEntity<List<PensumItemDto.Response>> getVersion(
            @PathVariable("codeProgram") String codeProgram,
            @PathVariable("version") Integer version) {
        return ResponseEntity.ok(pensumService.getVersion(codeProgram, version));
    }

    @PostMapping("/programa/{codeProgram}/guardar")
    public ResponseEntity<List<PensumItemDto.Response>> saveVersion(
            @PathVariable("codeProgram") String codeProgram,
            @Valid @RequestBody PensumItemDto.SaveRequest request) {
        if (!codeProgram.equalsIgnoreCase(request.codeProgram())) {
            throw new co.edu.univalle.palmira.exception.BadRequestException(
                    "El programa de la ruta no coincide con el cuerpo de la solicitud");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pensumService.saveVersion(request));
    }

    @PutMapping("/programa/{codeProgram}/editar")
    @Operation(summary = "Actualizar la malla curricular activa de un programa (HU-032)")
    public ResponseEntity<List<PensumItemDto.Response>> updateActiveVersion(
            @PathVariable("codeProgram") String codeProgram,
            @Valid @RequestBody PensumItemDto.SaveRequest request) {
        return ResponseEntity.ok(pensumService.updateActiveVersion(codeProgram, request));
    }

    @PatchMapping("/programa/{codeProgram}/desactivar")
    public ResponseEntity<Void> deactivate(@PathVariable("codeProgram") String codeProgram) {
        pensumService.deactivateCurrent(codeProgram);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/programa/{codeProgram}/version/{version}/reactivar")
    public ResponseEntity<List<PensumItemDto.Response>> reactivate(
            @PathVariable("codeProgram") String codeProgram,
            @PathVariable("version") Integer version) {
        return ResponseEntity.ok(pensumService.reactivate(codeProgram, version));
    }

    @PostMapping
    @Operation(summary = "Agregar una asignatura al pensum de un programa")
    public ResponseEntity<PensumItemDto.Response> addCourseToPensum(
            @Valid @RequestBody PensumItemDto.AddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pensumService.addCourseToPensum(request));
    }

    @PutMapping("/semestre")
    @Operation(summary = "Actualizar el semestre de una asignatura en el pensum")
    public ResponseEntity<PensumItemDto.Response> updateSemester(
            @Valid @RequestBody PensumItemDto.UpdateSemesterRequest request) {
        return ResponseEntity.ok(pensumService.updateCourseSemester(request));
    }

    @PatchMapping("/{codeProgram}/{codeCourse}/estado")
    @Operation(summary = "Habilitar o deshabilitar una asignatura en el pensum")
    public ResponseEntity<PensumItemDto.Response> cambiarEstado(
            @PathVariable("codeProgram") String codeProgram,
            @PathVariable("codeCourse") String codeCourse,
            @RequestParam("activo") Boolean activo) {
        return ResponseEntity.ok(pensumService.cambiarEstado(codeProgram, codeCourse, activo));
    }

    @DeleteMapping("/{codeProgram}/{codeCourse}")
    @Operation(summary = "Eliminar una asignatura del pensum de un programa")
    public ResponseEntity<Void> removeCourseFromPensum(
            @PathVariable("codeProgram") String codeProgram,
            @PathVariable("codeCourse") String codeCourse) {
        pensumService.removeCourseFromPensum(codeProgram, codeCourse);
        return ResponseEntity.noContent().build();
    }
}
