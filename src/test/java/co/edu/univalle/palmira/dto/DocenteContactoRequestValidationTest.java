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
 * HU-015: validaciones del correo personal y del teléfono (decisiones del 2026-10-25).
 */
class DocenteContactoRequestValidationTest {

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

    private Set<String> mensajes(String correoPersonal, String telefono) {
        return validator.validate(new DocenteDto.ContactoRequest(correoPersonal, telefono))
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("HU-015 C2: correo personal y teléfono son opcionales")
    void contactoOpcional() {
        assertTrue(mensajes(null, null).isEmpty());
        assertTrue(mensajes("", "").isEmpty());
    }

    @Test
    @DisplayName("HU-015 C2: un correo personal y un teléfono válidos no tienen errores")
    void contactoValido() {
        assertTrue(mensajes("ana.prueba@example.com", "3001234567").isEmpty());
        assertTrue(mensajes("ana@example.com", "2724000").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana", "ana@", "ana@example", "@example.com", "ana example@correo.com"})
    @DisplayName("HU-015 C2: correo personal mal formado muestra «El correo personal no tiene un formato válido»")
    void correoPersonalFormatoInvalido(String correo) {
        assertTrue(mensajes(correo, null).contains("El correo personal no tiene un formato válido"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@correounivalle.edu.co", "Ana@CorreoUnivalle.EDU.CO"})
    @DisplayName("HU-015 C2: correo institucional como personal muestra «El correo personal no puede ser institucional»")
    void correoPersonalInstitucional(String correo) {
        assertEquals(Set.of("El correo personal no puede ser institucional"), mensajes(correo, null));
    }

    @Test
    @DisplayName("HU-015 C2: correo personal de más de 100 caracteres no es válido")
    void correoPersonalLargo() {
        String correo = "a".repeat(89) + "@example.com"; // 101 caracteres
        assertTrue(mensajes(correo, null).contains("El correo personal no puede exceder 100 caracteres"));
        assertTrue(mensajes("a".repeat(88) + "@example.com", null).isEmpty(), "100 caracteres es válido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "12345678901", "300-123-4567", "300 1234567", "+573001234567", "abcdefg"})
    @DisplayName("HU-015 C2: teléfono que no tenga de 7 a 10 dígitos muestra «El teléfono solo admite de 7 a 10 dígitos»")
    void telefonoInvalido(String telefono) {
        assertEquals(Set.of("El teléfono solo admite de 7 a 10 dígitos"), mensajes(null, telefono));
    }
}
