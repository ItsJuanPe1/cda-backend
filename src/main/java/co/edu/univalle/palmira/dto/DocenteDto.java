package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.CategoriaDocente;
import co.edu.univalle.palmira.model.enums.TipoDocente;
import co.edu.univalle.palmira.model.enums.TipoDocumento;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DocenteDto {

    public record CreateRequest(
            @JsonProperty("codigo") @NotBlank(message = "El código del docente es obligatorio") @Size(max = 20, message = "El código no puede exceder 20 caracteres") String codigo,

            @JsonProperty("nombres") @NotBlank(message = "Los nombres son obligatorios") @Size(max = 30, message = "Los nombres no pueden exceder 30 caracteres") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "Los nombres solo pueden contener letras y espacios") String nombres,

            @JsonProperty("apellidos") @NotBlank(message = "Los apellidos son obligatorios") @Size(max = 30, message = "Los apellidos no pueden exceder 30 caracteres") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "Los apellidos solo pueden contener letras y espacios") String apellidos,

            @JsonProperty("tipo_documento") @NotNull(message = "El tipo de documento es obligatorio") TipoDocumento tipoDocumento,

            @JsonProperty("numero_documento") @NotBlank(message = "El número de documento es obligatorio") @Size(max = 11, message = "El número de documento no puede exceder 11 caracteres") @Pattern(regexp = "^[0-9]+$", message = "El número de documento solo puede contener números") String numeroDocumento,

            @JsonProperty("correo_institucional") @NotBlank(message = "El correo institucional es obligatorio") @Size(max = 50, message = "El correo no puede exceder 50 caracteres") @Email(message = "El correo debe tener un formato válido") @Pattern(regexp = "^[a-zA-Z0-9._%+\\-]+@correounivalle\\.edu\\.co$", message = "El correo debe tener dominio @correounivalle.edu.co") String correoInstitucional,

            @JsonProperty("telefono") @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres") String telefono,

            @JsonProperty("tipo_docente") @NotNull(message = "El tipo de docente es obligatorio") TipoDocente tipoDocente,

            @JsonProperty("categoria") @NotNull(message = "La categoría es obligatoria") CategoriaDocente categoria,

            @JsonProperty("code_program") @NotBlank(message = "El programa de adscripción es obligatorio") String codeProgram,

            @JsonProperty("foto_url") String fotoUrl,

            @JsonProperty("evaluacion_periodo") @Size(max = 10, message = "El periodo no puede exceder 10 caracteres") String evaluacionPeriodo,

            @JsonProperty("evaluacion_valor") BigDecimal evaluacionValor) {
    }

    public record UpdateRequest(
            @JsonProperty("codigo") @Size(max = 20, message = "El código no puede exceder 20 caracteres") String codigo,

            @JsonProperty("nombres") @Size(max = 30, message = "Los nombres no pueden exceder 30 caracteres") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "Los nombres solo pueden contener letras y espacios") String nombres,

            @JsonProperty("apellidos") @Size(max = 30, message = "Los apellidos no pueden exceder 30 caracteres") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "Los apellidos solo pueden contener letras y espacios") String apellidos,

            @JsonProperty("tipo_documento") TipoDocumento tipoDocumento,

            @JsonProperty("numero_documento") @Size(max = 11, message = "El número de documento no puede exceder 11 caracteres") @Pattern(regexp = "^[0-9]+$", message = "El número de documento solo puede contener números") String numeroDocumento,

            @JsonProperty("correo_institucional") @Size(max = 50, message = "El correo no puede exceder 50 caracteres") @Email(message = "El correo debe tener un formato válido") @Pattern(regexp = "^[a-zA-Z0-9._%+\\-]+@correounivalle\\.edu\\.co$", message = "El correo debe tener dominio @correounivalle.edu.co") String correoInstitucional,

            @JsonProperty("telefono") @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres") String telefono,

            @JsonProperty("tipo_docente") TipoDocente tipoDocente,

            @JsonProperty("categoria") CategoriaDocente categoria,

            @JsonProperty("code_program") String codeProgram,

            @JsonProperty("foto_url") String fotoUrl,

            @JsonProperty("evaluacion_periodo") @Size(max = 10, message = "El periodo no puede exceder 10 caracteres") String evaluacionPeriodo,

            @JsonProperty("evaluacion_valor") BigDecimal evaluacionValor) {
    }

    public record Response(
            @JsonProperty("id") Long id,
            @JsonProperty("codigo") String codigo,
            @JsonProperty("nombres") String nombres,
            @JsonProperty("apellidos") String apellidos,
            @JsonProperty("tipo_documento") TipoDocumento tipoDocumento,
            @JsonProperty("numero_documento") String numeroDocumento,
            @JsonProperty("correo_institucional") String correoInstitucional,
            @JsonProperty("telefono") String telefono,
            @JsonProperty("tipo_docente") TipoDocente tipoDocente,
            @JsonProperty("categoria") CategoriaDocente categoria,
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("nombre_programa") String nombrePrograma,
            @JsonProperty("foto_url") String fotoUrl,
            @JsonProperty("evaluacion_periodo") String evaluacionPeriodo,
            @JsonProperty("evaluacion_valor") BigDecimal evaluacionValor,
            @JsonProperty("estado") Boolean estado,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt) {
    }
}
