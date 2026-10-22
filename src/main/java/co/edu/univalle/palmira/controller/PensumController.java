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
    public ResponseEntity<List<PensumItemDto.Response>> getByProgram(@PathVariable String codeProgram) {
        return ResponseEntity.ok(pensumService.getByProgram(codeProgram));
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

    @DeleteMapping("/{codeProgram}/{codeCourse}")
    @Operation(summary = "Eliminar una asignatura del pensum de un programa")
    public ResponseEntity<Void> removeCourseFromPensum(
            @PathVariable String codeProgram,
            @PathVariable String codeCourse) {
        pensumService.removeCourseFromPensum(codeProgram, codeCourse);
        return ResponseEntity.noContent().build();
    }
}
