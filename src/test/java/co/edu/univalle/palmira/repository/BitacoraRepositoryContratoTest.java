package co.edu.univalle.palmira.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** HU-058 criterio 4: el repositorio de la bitácora solo inserta y consulta. */
class BitacoraRepositoryContratoTest {

    @Test
    @DisplayName("Criterio 4: el repositorio no expone métodos para modificar ni borrar")
    void criterio4_repositorioSinUpdateNiDelete() {
        assertFalse(CrudRepository.class.isAssignableFrom(BitacoraRepository.class),
                "No debe heredar de CrudRepository/JpaRepository");

        List<String> metodos = Arrays.stream(BitacoraRepository.class.getMethods())
                .map(Method::getName)
                .map(n -> n.toLowerCase(Locale.ROOT))
                .toList();
        assertTrue(metodos.stream().noneMatch(n -> n.contains("delete") || n.contains("remove")
                || n.contains("update") || n.contains("modif")), "Métodos expuestos: " + metodos);
        assertEquals(List.of("buscar", "save"), metodos.stream().sorted().toList());
    }
}
