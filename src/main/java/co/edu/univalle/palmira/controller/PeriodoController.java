package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.PeriodoDto;
import co.edu.univalle.palmira.service.PeriodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/periodos")
@RequiredArgsConstructor
@Tag(name = "Períodos", description = "Resoluciones de calendario académico (solo administrador)")
public class PeriodoController {

    private final PeriodoService periodoService;

    @GetMapping
    @Operation(summary = "Listar períodos (del más reciente al más antiguo)")
    public ResponseEntity<List<PeriodoDto.Resumen>> getAll() {
        return ResponseEntity.ok(periodoService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener el detalle de un período")
    public ResponseEntity<PeriodoDto.Detalle> getById(@PathVariable Long id) {
        return ResponseEntity.ok(periodoService.getById(id));
    }
}