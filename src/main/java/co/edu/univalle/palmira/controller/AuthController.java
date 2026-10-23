package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.GoogleLoginRequest;
import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.service.GoogleAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Endpoints para inicio de sesión con Google y gestión de credenciales")
public class AuthController {
    private final GoogleAuthService googleAuthService;

    public AuthController(GoogleAuthService googleAuthService) {
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/google")
    @Operation(summary = "Iniciar sesión con Google ID Token")
    public LoginResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return googleAuthService.login(request);
    }
}
