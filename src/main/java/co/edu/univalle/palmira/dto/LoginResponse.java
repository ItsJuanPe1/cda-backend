package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        @JsonProperty("token")
        String token,

        @JsonProperty("tokenType")
        @JsonAlias("token_type")
        String tokenType,

        @JsonProperty("expiresInSeconds")
        @JsonAlias("expires_in_seconds")
        long expiresInSeconds,

        @JsonProperty("id")
        Integer id,

        @JsonProperty("nombre")
        String nombre,

        @JsonProperty("apellido")
        String apellido,

        @JsonProperty("email")
        String email,

        @JsonProperty("rol")
        Integer rol,

        @JsonProperty("codeProgram")
        @JsonAlias("code_program")
        String codeProgram,

        @JsonProperty("nombrePrograma")
        @JsonAlias("nombre_programa")
        String nombrePrograma,

        @JsonProperty("mensaje")
        String mensaje
) {
    public LoginResponse(String token, String tokenType, long expiresInSeconds, Integer id,
                         String nombre, String apellido, String email, Integer rol, String mensaje) {
        this(token, tokenType, expiresInSeconds, id, nombre, apellido, email, rol, null, null, mensaje);
    }
}
