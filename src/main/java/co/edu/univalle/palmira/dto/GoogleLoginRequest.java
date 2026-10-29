package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "La credencial de Google es obligatoria")
        @JsonProperty("credential")
        @JsonAlias({"id_token", "idToken", "token"})
        String credential,

        @JsonProperty("rolEsperado")
        @JsonAlias({"rol_esperado", "role"})
        Integer rolEsperado
) {
    public GoogleLoginRequest(String credential) {
        this(credential, null);
    }
}
