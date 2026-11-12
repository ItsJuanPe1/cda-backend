package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.CargaDocenteDto;
import co.edu.univalle.palmira.service.CargaDocenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/carga")
@RequiredArgsConstructor
@Tag(name = "Carga académica docente", description = "Consulta de la carga horaria de los docentes por período (límite de 20 h)")
public class CargaDocenteController {

    private final CargaDocenteService cargaDocenteService;

    @GetMapping("/docentes/{id}")
    @Operation(summary = "Cursos, horas y total de un docente en un período. El coordinador solo ve el detalle de su programa")
    public ResponseEntity<CargaDocenteDto.Detalle> consultar(
            @PathVariable("id") Long id,
            @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(cargaDocenteService.consultar(id, periodoId));
    }

    @GetMapping("/docentes")
    @Operation(summary = "Total de horas de todos los docentes con cursos en un período")
    public ResponseEntity<List<CargaDocenteDto.Resumen>> listar(@RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(cargaDocenteService.listarPorPeriodo(periodoId));
    }
}