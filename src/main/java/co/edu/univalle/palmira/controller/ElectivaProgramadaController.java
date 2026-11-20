package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ElectivaProgramadaDto;
import co.edu.univalle.palmira.service.ElectivaProgramadaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/programacion/electivas")
@RequiredArgsConstructor
@Tag(name = "Electivas profesionales", description = "Programación manual de electivas profesionales por el coordinador")
public class ElectivaProgramadaController {

    private static final String ROLES = "hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')";

    private final ElectivaProgramadaService service;

    @GetMapping
    @PreAuthorize(ROLES)
    @Operation(summary = "Electivas programadas del programa en el período vigente")
    public ResponseEntity<ElectivaProgramadaDto.ListaResponse> listar(
            @RequestParam("codeProgram") String codeProgram) {
        return ResponseEntity.ok(service.listar(codeProgram));
    }

    @PostMapping
    @PreAuthorize(ROLES)
    @Operation(summary = "Programar una electiva profesional en un espacio de la malla")
    public ResponseEntity<ElectivaProgramadaDto.Response> crear(
            @RequestBody(required = false) ElectivaProgramadaDto.CrearRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PatchMapping("/{cursoId}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Cambiar la electiva o los cupos (solo sin docente asignado)")
    public ResponseEntity<ElectivaProgramadaDto.Response> modificar(
            @PathVariable("cursoId") Long cursoId,
            @RequestBody(required = false) ElectivaProgramadaDto.ModificarRequest request) {
        return ResponseEntity.ok(service.modificar(cursoId, request));
    }

    @DeleteMapping("/{cursoId}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Quitar una electiva programada (solo sin docente asignado)")
    public ResponseEntity<Void> quitar(@PathVariable("cursoId") Long cursoId) {
        service.quitar(cursoId);
        return ResponseEntity.noContent().build();
    }
}
