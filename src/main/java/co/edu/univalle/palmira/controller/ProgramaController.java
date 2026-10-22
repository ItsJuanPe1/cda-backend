package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ProgramaDto;
import co.edu.univalle.palmira.service.ProgramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/programas")
@RequiredArgsConstructor
@Tag(name = "Programas", description = "Endpoints para la gestión de programas académicos (carreras)")
public class ProgramaController {

    private final ProgramaService programaService;

    @GetMapping
    @Operation(summary = "Listar todos los programas académicos con conteo de asignaturas")
    public ResponseEntity<List<ProgramaDto.Response>> getAll() {
        return ResponseEntity.ok(programaService.getAll());
    }

    @GetMapping("/{codeProgram}")
    @Operation(summary = "Obtener un programa académico por código")
    public ResponseEntity<ProgramaDto.Response> getByCode(@PathVariable String codeProgram) {
        return ResponseEntity.ok(programaService.getByCode(codeProgram));
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo programa académico")
    public ResponseEntity<ProgramaDto.Response> create(@Valid @RequestBody ProgramaDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programaService.create(request));
    }

    @PutMapping("/{codeProgram}")
    @Operation(summary = "Actualizar un programa académico existente")
    public ResponseEntity<ProgramaDto.Response> update(
            @PathVariable String codeProgram,
            @Valid @RequestBody ProgramaDto.UpdateRequest request) {
        return ResponseEntity.ok(programaService.update(codeProgram, request));
    }

    @DeleteMapping("/{codeProgram}")
    @Operation(summary = "Eliminar un programa académico")
    public ResponseEntity<Void> delete(@PathVariable String codeProgram) {
        programaService.delete(codeProgram);
        return ResponseEntity.noContent().build();
    }
}
