package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.CargaDocenteDto;
import co.edu.univalle.palmira.service.CargaDocenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la consulta de la carga horaria semanal de los docentes.
 * <p>
 * Permite monitorear las horas asignadas frente al límite de 20 horas semanales.
 * </p>
 */
@RestController
@RequestMapping("/api/carga")
@Tag(name = "Carga academica docente", description = "Consulta de la carga horaria de los docentes por periodo (limite de 20 h)")
public class CargaDocenteController {

    private final CargaDocenteService cargaDocenteService;

    @Autowired
    public CargaDocenteController(CargaDocenteService cargaDocenteService) {
        this.cargaDocenteService = cargaDocenteService;
    }

    /**
     * Consulta el detalle de cursos asignados y horas acumuladas de un docente en un período.
     * Si el usuario es un coordinador, se aplica filtrado de visibilidad por programa propio.
     *
     * @param id        identificador del docente
     * @param periodoId identificador del período académico
     * @return detalle de cursos y horas acumuladas
     */
    @GetMapping("/docentes/{id}")
    @Operation(summary = "Cursos, horas y total de un docente en un periodo")
    public ResponseEntity<CargaDocenteDto.Detalle> consultar(
            @PathVariable("id") Long id,
            @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(cargaDocenteService.consultar(id, periodoId));
    }

    /**
     * Lista el consolidado de horas acumuladas de todos los docentes con cursos programados en un período.
     *
     * @param periodoId identificador del período académico
     * @return lista de resúmenes de carga docente
     */
    @GetMapping("/docentes")
    @Operation(summary = "Total de horas de todos los docentes con cursos en un periodo")
    public ResponseEntity<List<CargaDocenteDto.Resumen>> listar(@RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(cargaDocenteService.listarPorPeriodo(periodoId));
    }
}
