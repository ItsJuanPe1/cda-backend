package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class CoordinadorDto {

    public record Response(
            Integer id,
            @JsonProperty("docente_id") Long docenteId,
            String identificacion,
            @JsonProperty("nombre_completo") String nombreCompleto,
            String correo,
            @JsonProperty("correo_institucional") String correoInstitucional,
            @JsonProperty("docente_correo_institucional") String docenteCorreoInstitucional,
            String telefono,
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("name_program") String nameProgram,
            @JsonProperty("id_categoria") Integer idCategoria,
            String categoria,
            @JsonProperty("id_tipo_contrato") Integer idTipoContrato,
            @JsonProperty("tipo_contrato") String tipoContrato,
            Boolean activo,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}

    public record CreateRequest(
            @NotNull(message = "El docente es obligatorio")
            @JsonProperty("docente_id")
            Long docenteId,

            @NotBlank(message = "El programa académico es obligatorio")
            @JsonProperty("code_program")
            String codeProgram,

            @NotBlank(message = "El correo institucional de coordinación es obligatorio")
            @Email(message = "El correo institucional debe tener un formato válido")
            @Size(max = 150, message = "El correo institucional no puede exceder 150 caracteres")
            @JsonProperty("correo_institucional")
            String correoInstitucional,

            Boolean activo
    ) {}

    public record UpdateRequest(
            @JsonProperty("docente_id")
            Long docenteId,

            @JsonProperty("code_program")
            String codeProgram,

            @Email(message = "El correo institucional debe tener un formato válido")
            @Size(max = 150, message = "El correo institucional no puede exceder 150 caracteres")
            @JsonProperty("correo_institucional")
            String correoInstitucional,

            Boolean activo
    ) {}
}
