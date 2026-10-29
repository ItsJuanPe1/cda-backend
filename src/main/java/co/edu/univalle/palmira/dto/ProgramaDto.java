package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// HU-040: contrato en docs/contratos/HU-040.md
public class ProgramaDto {

    public static final String MSG_CODIGO = "El código debe tener 4 dígitos";
    public static final String MSG_NOMBRE_OBLIGATORIO = "El nombre es obligatorio";
    public static final String MSG_NOMBRE_LARGO = "El nombre no puede superar 100 caracteres";
    public static final String MSG_JORNADA = "La jornada debe ser diurna, nocturna o ambas";
    public static final String MSG_DURACION_DIURNA = "La duración en jornada diurna es obligatoria y debe estar entre 1 y 20";
    public static final String MSG_DURACION_NOCTURNA = "La duración en jornada nocturna es obligatoria y debe estar entre 1 y 20";
    public static final String MSG_INGRESO = "El ingreso debe ser semestral o anual";
    public static final String MSG_ESTADO = "El estado debe ser activo o inactivo";
    public static final String MSG_APERTURA = "La apertura debe ser semestral o anual";
    public static final String MSG_NUMERO_RESOLUCION_OBLIGATORIO = "El número de resolución es obligatorio";
    public static final String MSG_NUMERO_RESOLUCION_LARGO = "El número de resolución no puede superar 50 caracteres";

    private static final String REGEX_CODIGO = "^\\s*[0-9]{4}\\s*$";
    private static final String REGEX_NO_VACIO = "^(?s).*\\S.*$";
    private static final String REGEX_JORNADA = "^(DIURNA|NOCTURNA|AMBAS)$";
    private static final String REGEX_INGRESO = "^(SEMESTRAL|ANUAL)$";
    private static final String REGEX_ESTADO = "^(ACTIVO|INACTIVO)$";
    private static final String REGEX_APERTURA = "(?i)^(SEMESTRAL|ANUAL)$";

    public record Response(
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("name_program") String nameProgram,
            @JsonProperty("jornada") Jornada jornada,
            @JsonProperty("numero_semestre") Integer numeroSemestre,
            @JsonProperty("semestres_nocturna") Integer semestresNocturna,
            @JsonProperty("ingreso") TipoIngreso ingreso,
            @JsonProperty("estado") EstadoPrograma estado,
            @JsonProperty("id_facultad_programa") Integer idFacultadPrograma,
            @JsonProperty("total_asignaturas") Long totalAsignaturas,
            @JsonProperty("apertura") String apertura,
            @JsonProperty("numero_resolucion") String numeroResolucion
    ) {
        public Response(String codeProgram, String nameProgram, Jornada jornada,
                        Integer numeroSemestre, Integer semestresNocturna, TipoIngreso ingreso,
                        EstadoPrograma estado, Integer idFacultadPrograma, Long totalAsignaturas) {
            this(codeProgram, nameProgram, jornada, numeroSemestre, semestresNocturna, ingreso,
                    estado, idFacultadPrograma, totalAsignaturas, null, null);
        }

        public Response(String codeProgram, String nameProgram, Jornada jornada,
                        Integer numeroSemestre, Integer semestresNocturna, TipoIngreso ingreso,
                        EstadoPrograma estado, Integer idFacultadPrograma, Long totalAsignaturas,
                        String apertura) {
            this(codeProgram, nameProgram, jornada, numeroSemestre, semestresNocturna, ingreso,
                    estado, idFacultadPrograma, totalAsignaturas, apertura, null);
        }
    }


    // Las duraciones obligatorias según la jornada se validan en ProgramaService
    public record CreateRequest(
            @JsonProperty("code_program")
            @NotNull(message = MSG_CODIGO)
            @Pattern(regexp = REGEX_CODIGO, message = MSG_CODIGO)
            String codeProgram,

            @JsonProperty("name_program")
            @NotNull(message = MSG_NOMBRE_OBLIGATORIO)
            @Pattern(regexp = REGEX_NO_VACIO, message = MSG_NOMBRE_OBLIGATORIO)
            @Size(max = 100, message = MSG_NOMBRE_LARGO)
            String nameProgram,

            @JsonProperty("jornada")
            @NotNull(message = MSG_JORNADA)
            @Pattern(regexp = REGEX_JORNADA, message = MSG_JORNADA)
            String jornada,

            @JsonProperty("numero_semestre")
            @Min(value = 1, message = MSG_DURACION_DIURNA)
            @Max(value = 20, message = MSG_DURACION_DIURNA)
            Integer numeroSemestre,

            @JsonProperty("semestres_nocturna")
            @Min(value = 1, message = MSG_DURACION_NOCTURNA)
            @Max(value = 20, message = MSG_DURACION_NOCTURNA)
            Integer semestresNocturna,

            @JsonProperty("ingreso")
            @NotNull(message = MSG_INGRESO)
            @Pattern(regexp = REGEX_INGRESO, message = MSG_INGRESO)
            String ingreso,

            @JsonProperty("estado")
            @Pattern(regexp = REGEX_ESTADO, message = MSG_ESTADO)
            String estado,

            @JsonProperty("id_facultad_programa")
            Integer idFacultadPrograma,

            @JsonProperty("apertura")
            @Pattern(regexp = REGEX_APERTURA, message = MSG_APERTURA)
            String apertura,

            @JsonProperty("numero_resolucion")
            @NotBlank(message = MSG_NUMERO_RESOLUCION_OBLIGATORIO)
            @Size(max = 50, message = MSG_NUMERO_RESOLUCION_LARGO)
            String numeroResolucion
    ) {
        // Compatibilidad: sin apertura ni número de resolución
        public CreateRequest(String codeProgram, String nameProgram, String jornada,
                             Integer numeroSemestre, Integer semestresNocturna, String ingreso,
                             String estado, Integer idFacultadPrograma) {
            this(codeProgram, nameProgram, jornada, numeroSemestre, semestresNocturna,
                    ingreso, estado, idFacultadPrograma, null, null);
        }

        // Compatibilidad: sin número de resolución
        public CreateRequest(String codeProgram, String nameProgram, String jornada,
                             Integer numeroSemestre, Integer semestresNocturna, String ingreso,
                             String estado, Integer idFacultadPrograma, String apertura) {
            this(codeProgram, nameProgram, jornada, numeroSemestre, semestresNocturna,
                    ingreso, estado, idFacultadPrograma, apertura, null);
        }
    }

    // Actualización parcial: un campo ausente o null conserva su valor
    public record UpdateRequest(
            @JsonProperty("code_program")
            String codeProgram,

            @JsonProperty("name_program")
            @Pattern(regexp = REGEX_NO_VACIO, message = MSG_NOMBRE_OBLIGATORIO)
            @Size(max = 100, message = MSG_NOMBRE_LARGO)
            String nameProgram,

            @JsonProperty("jornada")
            @Pattern(regexp = REGEX_JORNADA, message = MSG_JORNADA)
            String jornada,

            @JsonProperty("numero_semestre")
            @Min(value = 1, message = MSG_DURACION_DIURNA)
            @Max(value = 20, message = MSG_DURACION_DIURNA)
            Integer numeroSemestre,

            @JsonProperty("semestres_nocturna")
            @Min(value = 1, message = MSG_DURACION_NOCTURNA)
            @Max(value = 20, message = MSG_DURACION_NOCTURNA)
            Integer semestresNocturna,

            @JsonProperty("ingreso")
            @Pattern(regexp = REGEX_INGRESO, message = MSG_INGRESO)
            String ingreso,

            @JsonProperty("estado")
            @Pattern(regexp = REGEX_ESTADO, message = MSG_ESTADO)
            String estado,

            @JsonProperty("id_facultad_programa")
            Integer idFacultadPrograma,

            @JsonProperty("apertura")
            @Pattern(regexp = REGEX_APERTURA, message = MSG_APERTURA)
            String apertura,

            // Ausente o null conserva el valor; si llega, no puede estar en blanco.
            @JsonProperty("numero_resolucion")
            @Pattern(regexp = REGEX_NO_VACIO, message = MSG_NUMERO_RESOLUCION_OBLIGATORIO)
            @Size(max = 50, message = MSG_NUMERO_RESOLUCION_LARGO)
            String numeroResolucion,

            // Solo se envía cuando el administrador realmente cambia la asignación.
            @JsonProperty("id_coordinador_nuevo")
            @Positive(message = "El ID del coordinador debe ser positivo")
            Integer idCoordinadorNuevo
    ) {
        // Compatibilidad con llamadas que todavía usan los 10 campos anteriores.
        public UpdateRequest(String codeProgram, String nameProgram, String jornada,
                             Integer numeroSemestre, Integer semestresNocturna, String ingreso,
                             String estado, Integer idFacultadPrograma, String apertura,
                             String numeroResolucion) {
            this(codeProgram, nameProgram, jornada, numeroSemestre, semestresNocturna,
                    ingreso, estado, idFacultadPrograma, apertura, numeroResolucion, null);
        }

        // Compatibilidad: sin apertura ni número de resolución.
        public UpdateRequest(String codeProgram, String nameProgram, String jornada,
                             Integer numeroSemestre, Integer semestresNocturna, String ingreso,
                             String estado, Integer idFacultadPrograma) {
            this(codeProgram, nameProgram, jornada, numeroSemestre, semestresNocturna,
                    ingreso, estado, idFacultadPrograma, null, null, null);
        }

        // Compatibilidad: sin número de resolución.
        public UpdateRequest(String codeProgram, String nameProgram, String jornada,
                             Integer numeroSemestre, Integer semestresNocturna, String ingreso,
                             String estado, Integer idFacultadPrograma, String apertura) {
            this(codeProgram, nameProgram, jornada, numeroSemestre, semestresNocturna,
                    ingreso, estado, idFacultadPrograma, apertura, null, null);
        }
    }
}
