package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class CoordinadorDto {

    public record Response(
            Integer id,
            String identificacion,
            @JsonProperty("nombre_completo") String nombreCompleto,
            String correo,
            @JsonProperty("correo_institucional") String correoInstitucional,
            String telefono,
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("name_program") String nameProgram,
            String categoria,
            @JsonProperty("tipo_contrato") String tipoContrato,
            Boolean activo,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}

    public record CreateRequest(
            @NotBlank(message = "La identificación es obligatoria")
            @Size(max = 50, message = "La identificación no puede exceder 50 caracteres")
            String identificacion,

            @NotBlank(message = "El nombre completo es obligatorio")
            @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
            @JsonProperty("nombre_completo")
            String nombreCompleto,

            @Email(message = "El correo personal debe tener un formato válido")
            @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
            String correo,

            @NotBlank(message = "El correo institucional es obligatorio")
            @Email(message = "El correo institucional debe tener un formato válido")
            @Size(max = 150, message = "El correo institucional no puede exceder 150 caracteres")
            @JsonProperty("correo_institucional")
            String correoInstitucional,

            @Size(max = 50, message = "El teléfono no puede exceder 50 caracteres")
            String telefono,

            @JsonProperty("code_program")
            String codeProgram,

            String categoria,

            @JsonProperty("tipo_contrato")
            String tipoContrato,

            Boolean activo
    ) {}

    public record UpdateRequest(
            @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
            @JsonProperty("nombre_completo")
            String nombreCompleto,

            @Email(message = "El correo personal debe tener un formato válido")
            @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
            String correo,

            @Email(message = "El correo institucional debe tener un formato válido")
            @Size(max = 150, message = "El correo institucional no puede exceder 150 caracteres")
            @JsonProperty("correo_institucional")
            String correoInstitucional,

            @Size(max = 50, message = "El teléfono no puede exceder 50 caracteres")
            String telefono,

            @JsonProperty("code_program")
            String codeProgram,

            String categoria,

            @JsonProperty("tipo_contrato")
            String tipoContrato,

            Boolean activo
    ) {}
}
