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

            @JsonProperty("id_facultad") Integer idFacultad,

            @JsonProperty("code_program") String codeProgram,

            @JsonProperty("foto_url") String fotoUrl,

            @JsonProperty("evaluacion_periodo") @Size(max = 10, message = "El periodo no puede exceder 10 caracteres") String evaluacionPeriodo,

            @JsonProperty("evaluacion_valor") BigDecimal evaluacionValor) {

        public CreateRequest(
                String codigo, String nombres, String apellidos, TipoDocumento tipoDocumento,
                String numeroDocumento, String correoInstitucional, String telefono,
                TipoDocente tipoDocente, CategoriaDocente categoria, String codeProgram,
                String fotoUrl, String evaluacionPeriodo, BigDecimal evaluacionValor) {
            this(codigo, nombres, apellidos, tipoDocumento, numeroDocumento, correoInstitucional,
                    telefono, tipoDocente, categoria, null, codeProgram, fotoUrl, evaluacionPeriodo, evaluacionValor);
        }
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

            @JsonProperty("id_facultad") Integer idFacultad,

            @JsonProperty("code_program") String codeProgram,

            @JsonProperty("foto_url") String fotoUrl,

            @JsonProperty("evaluacion_periodo") @Size(max = 10, message = "El periodo no puede exceder 10 caracteres") String evaluacionPeriodo,

            @JsonProperty("evaluacion_valor") BigDecimal evaluacionValor) {

        public UpdateRequest(
                String codigo, String nombres, String apellidos, TipoDocumento tipoDocumento,
                String numeroDocumento, String correoInstitucional, String telefono,
                TipoDocente tipoDocente, CategoriaDocente categoria, String codeProgram,
                String fotoUrl, String evaluacionPeriodo, BigDecimal evaluacionValor) {
            this(codigo, nombres, apellidos, tipoDocumento, numeroDocumento, correoInstitucional,
                    telefono, tipoDocente, categoria, null, codeProgram, fotoUrl, evaluacionPeriodo, evaluacionValor);
        }
    }

    // HU-015: datos de contacto que edita el propio docente desde "Mi perfil".
    // Ambos son opcionales: null o vacío deja el campo sin valor.
    public record ContactoRequest(
            @JsonProperty("correo_personal")
            @Size(max = 100, message = "El correo personal no puede exceder 100 caracteres")
            @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}\\s*$", message = "El correo personal no tiene un formato válido")
            @Pattern(regexp = "^(?!.*@correounivalle\\.edu\\.co\\s*$).*$", flags = Pattern.Flag.CASE_INSENSITIVE, message = "El correo personal no puede ser institucional")
            String correoPersonal,

            @JsonProperty("telefono")
            @Pattern(regexp = "^\\s*$|^[0-9]{7,10}$", message = "El teléfono solo admite de 7 a 10 dígitos")
            String telefono) {
    }

    // HU-015: contenido de la foto activa para descargarla
    public record Foto(String tipoMime, byte[] contenido) {
    }

    public record Response(
            @JsonProperty("id") Long id,
            @JsonProperty("codigo") String codigo,
            @JsonProperty("nombres") String nombres,
            @JsonProperty("apellidos") String apellidos,
            @JsonProperty("tipo_documento") TipoDocumento tipoDocumento,
            @JsonProperty("numero_documento") String numeroDocumento,
            @JsonProperty("correo_institucional") String correoInstitucional,
            @JsonProperty("correo_personal") String correoPersonal,
            @JsonProperty("telefono") String telefono,
            @JsonProperty("tipo_docente") TipoDocente tipoDocente,
            @JsonProperty("categoria") CategoriaDocente categoria,
            @JsonProperty("id_facultad") Integer idFacultad,
            @JsonProperty("nombre_facultad") String nombreFacultad,
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("nombre_programa") String nombrePrograma,
            @JsonProperty("foto_url") String fotoUrl,
            @JsonProperty("tiene_foto") Boolean tieneFoto,
            @JsonProperty("evaluacion_periodo") String evaluacionPeriodo,
            @JsonProperty("evaluacion_valor") BigDecimal evaluacionValor,
            @JsonProperty("estado") Boolean estado,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt) {

        public Response(
                Long id, String codigo, String nombres, String apellidos,
                TipoDocumento tipoDocumento, String numeroDocumento,
                String correoInstitucional, String correoPersonal, String telefono,
                TipoDocente tipoDocente, CategoriaDocente categoria,
                String codeProgram, String nombrePrograma, String fotoUrl, Boolean tieneFoto,
                String evaluacionPeriodo, BigDecimal evaluacionValor,
                Boolean estado, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, codigo, nombres, apellidos, tipoDocumento, numeroDocumento,
                    correoInstitucional, correoPersonal, telefono, tipoDocente, categoria,
                    null, null, codeProgram, nombrePrograma, fotoUrl, tieneFoto,
                    evaluacionPeriodo, evaluacionValor, estado, createdAt, updatedAt);
        }
    }
}
