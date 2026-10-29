package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ProgramaDto;
import co.edu.univalle.palmira.service.ProgramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<ProgramaDto.Response> getByCode(@PathVariable("codeProgram") String codeProgram) {
        return ResponseEntity.ok(programaService.getByCode(codeProgram));
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo programa académico")
    public ResponseEntity<ProgramaDto.Response> create(@Valid @RequestBody ProgramaDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programaService.create(request));
    }

    @PutMapping("/{codeProgram}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Actualizar un programa académico existente (incluye intercambio de coordinador para admin)")
    public ResponseEntity<ProgramaDto.Response> update(
            @PathVariable("codeProgram") String codeProgram,
            @Valid @RequestBody ProgramaDto.UpdateRequest request) {
        return ResponseEntity.ok(programaService.update(codeProgram, request));
    }

    // HU-040 y RN-13: no borra, desactiva el programa
    @DeleteMapping("/{codeProgram}")
    @Operation(summary = "Desactivar un programa académico (no lo borra)")
    public ResponseEntity<Void> delete(@PathVariable("codeProgram") String codeProgram) {
        programaService.desactivar(codeProgram);
        return ResponseEntity.noContent().build();
    }
}
