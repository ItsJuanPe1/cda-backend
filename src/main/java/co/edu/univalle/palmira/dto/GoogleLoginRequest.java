package co.edu.univalle.palmira.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "La credencial de Google es obligatoria")
        String credential,
        Integer rolEsperado
) {
    public GoogleLoginRequest(String credential) {
        this(credential, null);
    }
}
