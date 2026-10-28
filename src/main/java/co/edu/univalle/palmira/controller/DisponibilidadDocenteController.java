package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.service.DisponibilidadDocenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/disponibilidades")
@RequiredArgsConstructor
public class DisponibilidadDocenteController {
    private final DisponibilidadDocenteService service;

    @GetMapping("/me")
    public DisponibilidadDocenteDto.Response consultar(@AuthenticationPrincipal Jwt jwt) {
        return service.consultar(jwt.getClaimAsString("email"));
    }

    @PostMapping("/me")
    public DisponibilidadDocenteDto.Item registrar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DisponibilidadDocenteDto.Request request) {
        return service.registrar(jwt.getClaimAsString("email"), request);
    }
}
