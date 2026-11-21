package co.edu.univalle.palmira.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dirección de vivienda del docente: opcional, hasta 150 caracteres y sin caracteres raros. */
class DocenteDireccionValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrir() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    private Set<String> mensajes(String direccion) {
        return validator.validate(new DocenteDto.ContactoRequest(null, null, null, null, direccion)).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Calle 5 # 12-34", "Cra. 27 No. 4-50, Apto 301", "Mz C Casa 12 / Barrio Ñ",
            "Avenida 3N # 45 - 10 Torre 2 Apto 1502"})
    void direccionValidaOVacia_noTieneErrores(String direccion) {
        assertTrue(mensajes(direccion).isEmpty());
    }

    @Test
    void direccionDeMasDe150Caracteres_seRechaza() {
        assertEquals(Set.of("La dirección no puede exceder 150 caracteres"), mensajes("a".repeat(151)));
        assertTrue(mensajes("a".repeat(150)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Calle 5 <script>", "Cra 3; DROP TABLE", "Calle 5 @ 3", "Calle {5}"})
    void direccionConCaracteresNoPermitidos_seRechaza(String direccion) {
        assertEquals(Set.of("La dirección solo admite letras, números y los signos # - . , / °"),
                mensajes(direccion));
    }

    @Test
    void elAdministradorTambienLaValida_alEditarElDocente() {
        DocenteDto.UpdateRequest ok = new DocenteDto.UpdateRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "Calle 5 # 12-34");
        DocenteDto.UpdateRequest mala = new DocenteDto.UpdateRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "Calle <5>");
        assertTrue(validator.validate(ok).isEmpty());
        assertEquals(1, validator.validate(mala).size());
    }
}
