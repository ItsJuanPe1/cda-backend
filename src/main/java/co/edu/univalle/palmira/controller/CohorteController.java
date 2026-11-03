package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.CohorteDto;
import co.edu.univalle.palmira.service.CohorteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cohortes")
@RequiredArgsConstructor
@Tag(name = "Cohortes Académicas", description = "Endpoints para la gestión y propuesta de cohortes activas por período")
public class CohorteController {

    private final CohorteService cohorteService;

    @GetMapping
    @Operation(summary = "Consultar cohortes de un período (opcionalmente filtrado por programa)")
    public ResponseEntity<List<CohorteDto.Response>> obtenerCohortes(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam(value = "codeProgram", required = false) String codeProgram) {
        if (codeProgram != null && !codeProgram.isBlank()) {
            return ResponseEntity.ok(cohorteService.obtenerCohortesPorPeriodoYPrograma(periodoId, codeProgram));
        }
        return ResponseEntity.ok(cohorteService.obtenerCohortesPorPeriodo(periodoId));
    }

    @PostMapping
    @Operation(summary = "Guardar o actualizar cohortes de un programa en un período")
    public ResponseEntity<List<CohorteDto.Response>> guardarCohortes(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam("codeProgram") String codeProgram,
            @Valid @RequestBody CohorteDto.SaveBatchRequest request) {
        return ResponseEntity.ok(cohorteService.guardarCohortes(periodoId, codeProgram, request.getCohortes()));
    }

    @GetMapping("/propuesta")
    @Operation(summary = "Obtener propuesta automática de cohortes para el siguiente período")
    public ResponseEntity<List<CohorteDto.PropuestaResponse>> proponerSiguientePeriodo(
            @RequestParam("periodoActualId") Long periodoActualId,
            @RequestParam(value = "periodoAnteriorId", required = false) Long periodoAnteriorId,
            @RequestParam(value = "confirmarAvanceForzado", defaultValue = "false") boolean confirmarAvanceForzado) {
        return ResponseEntity.ok(cohorteService.proponerSiguientePeriodo(periodoActualId, periodoAnteriorId, confirmarAvanceForzado));
    }
}
