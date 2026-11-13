package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.MisCursosDto;
import co.edu.univalle.palmira.service.MisCursosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carga/mis-cursos")
@RequiredArgsConstructor
@Tag(name = "Mis cursos (docente)", description = "Cursos que el docente autenticado tiene asignados en el período vigente")
public class MisCursosController {

    private final MisCursosService misCursosService;

    @GetMapping
    @Operation(summary = "Cursos, horas y total del docente autenticado en el período vigente")
    public ResponseEntity<MisCursosDto.Respuesta> misCursos(@AuthenticationPrincipal Jwt jwt) {
        String correo = jwt != null ? jwt.getClaimAsString("email") : null;
        return ResponseEntity.ok(misCursosService.consultar(correo));
    }
}