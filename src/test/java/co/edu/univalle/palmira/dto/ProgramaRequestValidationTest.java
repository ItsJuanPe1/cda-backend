package co.edu.univalle.palmira.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-040 criterios 1 y 2: validaciones del formulario de programa (docs/contratos/HU-040.md).
 */
class ProgramaRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private Set<String> mensajes(Object request) {
        return validator.validate(request)
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private ProgramaDto.CreateRequest crear(String codigo, String nombre, String jornada, String ingreso, String estado) {
        return new ProgramaDto.CreateRequest(codigo, nombre, jornada, 6, null, ingreso, estado, null);
    }

    @Test
    @DisplayName("Criterio 1: un formulario completo y válido no tiene errores")
    void formularioValido() {
        assertTrue(mensajes(crear("2724", "Tecnología Prueba", "DIURNA", "SEMESTRAL", "ACTIVO")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"272", "27245", "2724-DIU", "ABCD", ""})
    @DisplayName("Criterio 1: el código debe tener exactamente 4 dígitos")
    void codigoDeCuatroDigitos(String codigo) {
        assertTrue(mensajes(crear(codigo, "Programa", "DIURNA", "SEMESTRAL", null))
                .contains("El código debe tener 4 dígitos"));
    }

    @Test
    @DisplayName("Criterio 2: campos obligatorios vacíos muestran su mensaje")
    void obligatorios() {
        Set<String> m = mensajes(new ProgramaDto.CreateRequest(null, "   ", null, 6, null, null, null, null));

        assertTrue(m.contains("El código debe tener 4 dígitos"));
        assertTrue(m.contains("El nombre es obligatorio"));
        assertTrue(m.contains("La jornada debe ser diurna, nocturna o ambas"));
        assertTrue(m.contains("El ingreso debe ser semestral o anual"));
    }

    @Test
    @DisplayName("Criterio 1: el nombre admite máximo 100 caracteres")
    void nombreMaximo100() {
        assertTrue(mensajes(crear("2724", "a".repeat(100), "DIURNA", "SEMESTRAL", null)).isEmpty());
        assertTrue(mensajes(crear("2724", "a".repeat(101), "DIURNA", "SEMESTRAL", null))
                .contains("El nombre no puede superar 100 caracteres"));
    }

    @Test
    @DisplayName("Criterio 1: jornada, ingreso y estado solo aceptan los valores definidos")
    void valoresPermitidos() {
        Set<String> m = mensajes(crear("2724", "Programa", "MIXTA", "TRIMESTRAL", "BORRADO"));

        assertTrue(m.contains("La jornada debe ser diurna, nocturna o ambas"));
        assertTrue(m.contains("El ingreso debe ser semestral o anual"));
        assertTrue(m.contains("El estado debe ser activo o inactivo"));
    }

    @Test
    @DisplayName("Criterio 1: la duración debe estar entre 1 y 20 semestres")
    void duracionEnRango() {
        Set<String> m = mensajes(new ProgramaDto.CreateRequest("2724", "Programa", "AMBAS", 0, 21, "SEMESTRAL", null, null));

        assertTrue(m.contains(ProgramaDto.MSG_DURACION_DIURNA));
        assertTrue(m.contains(ProgramaDto.MSG_DURACION_NOCTURNA));
    }

    @Test
    @DisplayName("Criterio 4: al editar, los campos ausentes no se validan (actualización parcial)")
    void edicionParcial() {
        assertTrue(mensajes(new ProgramaDto.UpdateRequest(null, null, null, null, null, null, null, null)).isEmpty());
        assertTrue(mensajes(new ProgramaDto.UpdateRequest(null, " ", null, null, null, null, null, null))
                .contains("El nombre es obligatorio"));
    }
}
