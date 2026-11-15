package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.service.DisponibilidadDocenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestión de disponibilidad horaria docente.
 * <p>
 * Permite a los docentes consultar y registrar sus franjas semanales en el período activo,
 * y permite a los coordinadores y administradores consultar la disponibilidad de todos los docentes.
 * </p>
 */
@RestController
@RequestMapping("/api/disponibilidades")
@Tag(name = "Disponibilidad Docente", description = "Endpoints para el registro y consulta de franjas horarias de docentes")
public class DisponibilidadDocenteController {

    private final DisponibilidadDocenteService service;

    @Autowired
    public DisponibilidadDocenteController(DisponibilidadDocenteService service) {
        this.service = service;
    }

    /**
     * Consulta la disponibilidad registrada por el docente autenticado en el período actual.
     *
     * @param jwt token JWT del usuario autenticado
     * @return estado de disponibilidad con lista de franjas horarias
     */
    @GetMapping("/me")
    @Operation(summary = "Consultar la disponibilidad del docente autenticado")
    public DisponibilidadDocenteDto.Response consultar(@AuthenticationPrincipal Jwt jwt) {
        return service.consultar(jwt.getClaimAsString("email"));
    }

    /**
     * Consulta el mapa de disponibilidades de todos los docentes agrupadas por docenteId para un período.
     *
     * @param periodoId identificador del período académico
     * @return mapa donde la clave es el id del docente y el valor es la lista de franjas registradas
     */
    @GetMapping("/periodo/{periodoId}")
    @Operation(summary = "Consultar disponibilidad de todos los docentes en un período")
    public Map<Long, List<DisponibilidadDocenteDto.Item>> consultarPorPeriodo(
            @PathVariable("periodoId") Long periodoId) {
        return service.consultarPorPeriodo(periodoId);
    }

    /**
     * Registra una franja individual de disponibilidad horaria para el docente autenticado.
     *
     * @param jwt     token JWT del docente autenticado
     * @param request franja horaria a registrar (día, horaInicio, horaFin)
     * @return franja registrada
     */
    @PostMapping("/me")
    @Operation(summary = "Registrar una franja horaria individual")
    public DisponibilidadDocenteDto.Item registrar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DisponibilidadDocenteDto.Request request) {
        return service.registrar(jwt.getClaimAsString("email"), request);
    }

    /**
     * Reemplaza masivamente todas las franjas horarias de disponibilidad del docente para el período.
     *
     * @param jwt      token JWT del docente autenticado
     * @param requests lista completa de franjas horarias seleccionadas
     * @return lista de franjas horarias guardadas
     */
    @PutMapping("/me")
    @Operation(summary = "Actualizar masivamente las franjas de disponibilidad del docente")
    public List<DisponibilidadDocenteDto.Item> actualizarTodo(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody List<DisponibilidadDocenteDto.Request> requests) {
        return service.actualizarTodo(jwt.getClaimAsString("email"), requests);
    }
}
