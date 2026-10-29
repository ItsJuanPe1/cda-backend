package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PensumItemDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PensumId;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PensumServiceTest {

    @Mock
    private PensumAcademicoRepository pensumRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private AsignaturaRepository asignaturaRepository;

    @InjectMocks
    private PensumService pensumService;

    @Test
    @DisplayName("HU-032 Criterio 1: Rechaza editar si el programa no tiene malla activa")
    void updateActiveVersion_RechazaSiNoHayMallaActiva() {
        Programa prog = Programa.builder()
                .codeProgram("2724")
                .nameProgram("Tecnología en Desarrollo de Software")
                .jornada(Jornada.DIURNA)
                .ingreso(TipoIngreso.SEMESTRAL)
                .numeroSemestre(6)
                .build();

        when(programaRepository.findById("2724")).thenReturn(Optional.of(prog));
        when(pensumRepository.lockByProgram("2724")).thenReturn(List.of());

        PensumItemDto.SaveRequest request = new PensumItemDto.SaveRequest(
                "2724",
                List.of(new PensumItemDto.SaveItem("MAT101", 1, "Cálculo I", 4, 4, 8, "Asignatura básica", null)),
                "Tecnología en Desarrollo de Software",
                "Resolución actualizada",
                "DIURNA",
                "SEMESTRAL",
                6,
                "RES-045-2026"
        );

        ConflictException ex = assertThrows(ConflictException.class,
                () -> pensumService.updateActiveVersion("2724", request));
        assertTrue(ex.getMessage().contains("Solo las mallas activas se pueden editar"));
    }

    @Test
    @DisplayName("HU-032 Criterio 5: Lanza 'No hay cambios para guardar' cuando no se modificó nada")
    void updateActiveVersion_SinCambiosLanzaMensajeExacto() {
        Programa prog = Programa.builder()
                .codeProgram("2724")
                .nameProgram("Tecnología en Desarrollo de Software")
                .jornada(Jornada.DIURNA)
                .ingreso(TipoIngreso.SEMESTRAL)
                .numeroSemestre(6)
                .numeroResolucion("RES-010-2025")
                .build();

        Asignatura asig = Asignatura.builder()
                .codeCourse("MAT101")
                .nameCourse("Cálculo I")
                .credits(4)
                .esBasica(true)
                .build();

        PensumAcademico itemActivo = PensumAcademico.builder()
                .id(new PensumId("2724", "MAT101", 1))
                .semestre(1)
                .vigente(true)
                .activo(true)
                .numeroResolucion("RES-010-2025")
                .numeroSemestres(6)
                .facultad("META|hc=4|hi=8|tipo=Asignatura básica|desc=Resolucion vigente")
                .programa(prog)
                .asignatura(asig)
                .build();

        when(programaRepository.findById("2724")).thenReturn(Optional.of(prog));
        when(pensumRepository.lockByProgram("2724")).thenReturn(List.of(itemActivo));

        PensumItemDto.SaveRequest request = new PensumItemDto.SaveRequest(
                "2724",
                List.of(new PensumItemDto.SaveItem("MAT101", 1, "Cálculo I", 4, 4, 8, "Asignatura básica", "MAT101")),
                "Tecnología en Desarrollo de Software",
                "Resolucion vigente",
                "DIURNA",
                "SEMESTRAL",
                6,
                "RES-010-2025"
        );

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> pensumService.updateActiveVersion("2724", request));
        assertEquals("No hay cambios para guardar", ex.getMessage());
    }

    @Test
    @DisplayName("HU-044 / HU-017: Valida códigos duplicados y solo permite asignaturas existentes en el catálogo")
    void saveVersion_SeleccionaAsignaturasExistentesDelCatalogo() {
        Programa prog = Programa.builder()
                .codeProgram("2724")
                .nameProgram("Tecnología en Desarrollo de Software")
                .build();
        when(programaRepository.findById("2724")).thenReturn(Optional.of(prog));

        PensumItemDto.SaveRequest duplicados = new PensumItemDto.SaveRequest(
                "2724",
                List.of(
                        new PensumItemDto.SaveItem("MAT101", 1, "Cálculo I", 4, 4, 8, "Asignatura básica", null),
                        new PensumItemDto.SaveItem("MAT101", 1, "Álgebra Lineal", 3, 4, 8, "Asignatura básica", null)
                ),
                "Tecnología en Desarrollo de Software",
                "Resolución prueba",
                "DIURNA",
                "SEMESTRAL",
                6,
                "RES-001"
        );
        BadRequestException exDup = assertThrows(BadRequestException.class,
                () -> pensumService.saveVersion(duplicados));
        assertTrue(exDup.getMessage().contains("duplicado"));

        when(pensumRepository.lockByProgram("2724")).thenReturn(List.of());
        Asignatura electiva1 = Asignatura.builder()
                .codeCourse("ELE101").nameCourse("Electiva Profesional I").credits(3).activo(true).isElective(true).build();
        Asignatura electiva2 = Asignatura.builder()
                .codeCourse("ELE102").nameCourse("Electiva Profesional II").credits(3).activo(true).isElective(true).build();
        when(asignaturaRepository.findById("ELE101")).thenReturn(Optional.of(electiva1));
        when(asignaturaRepository.findById("ELE102")).thenReturn(Optional.of(electiva2));
        when(pensumRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PensumItemDto.SaveRequest request = new PensumItemDto.SaveRequest(
                "2724",
                List.of(
                        new PensumItemDto.SaveItem("ELE101", 1, "Nombre escrito en la malla", 8, 4, 8, "Profesional", null),
                        new PensumItemDto.SaveItem("ELE102", 4, "Otro nombre escrito en la malla", 9, 4, 8, "Profesional", null)
                ),
                "Tecnología en Desarrollo de Software",
                "Resolución con electivas profesionales",
                "DIURNA",
                "SEMESTRAL",
                8,
                "RES-2026-088"
        );

        List<PensumItemDto.Response> saved = pensumService.saveVersion(request);
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(item -> "ELE101".equals(item.codeCourse())
                && "Electiva Profesional I".equals(item.nameCourse()) && item.credits() == 3));
        assertEquals("RES-2026-088", saved.get(0).numeroResolucion());
        assertEquals(8, saved.get(0).numeroSemestres());
        assertFalse(saved.get(0).editada());
        verify(asignaturaRepository, never()).save(any(Asignatura.class));
    }

    @Test
    @DisplayName("HU-032 Criterio 2 y 6: Actualiza la malla activa y deja registro de edición (editada = true, fechaEdicion != null)")
    void updateActiveVersion_ExitosoYRegistraEdicion() {
        Programa prog = Programa.builder()
                .codeProgram("2724")
                .nameProgram("Tecnología en Desarrollo de Software")
                .jornada(Jornada.DIURNA)
                .ingreso(TipoIngreso.SEMESTRAL)
                .numeroSemestre(6)
                .numeroResolucion("RES-2025-01")
                .build();

        Asignatura asigExistente = Asignatura.builder()
                .codeCourse("MAT101")
                .nameCourse("Cálculo I")
                .credits(4)
                .esBasica(true)
                .build();

        PensumAcademico itemActivo = PensumAcademico.builder()
                .id(new PensumId("2724", "MAT101", 2))
                .semestre(1)
                .vigente(true)
                .activo(true)
                .numeroResolucion("RES-2025-01")
                .numeroSemestres(6)
                .editada(false)
                .facultad("META|hc=4|hi=8|tipo=Asignatura básica|desc=Resolucion anterior")
                .programa(prog)
                .asignatura(asigExistente)
                .build();

        when(programaRepository.findById("2724")).thenReturn(Optional.of(prog));
        when(pensumRepository.lockByProgram("2724"))
                .thenReturn(List.of(itemActivo));
        when(asignaturaRepository.findById("MAT101")).thenReturn(Optional.of(asigExistente));

        when(pensumRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PensumItemDto.SaveRequest request = new PensumItemDto.SaveRequest(
                "2724",
                List.of(new PensumItemDto.SaveItem("MAT101", 1, "Cálculo Diferencial", 3, 3, 6, "Asignatura básica", "MAT101")),
                "Tecnología en Desarrollo de Software",
                "Resolucion corregida",
                "NOCTURNA",
                "SEMESTRAL",
                7,
                "RES-2026-099"
        );

        List<PensumItemDto.Response> result = pensumService.updateActiveVersion("2724", request);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("RES-2026-099", result.get(0).numeroResolucion());
        assertEquals(7, result.get(0).numeroSemestres());
        assertTrue(result.get(0).editada());
        assertNotNull(result.get(0).fechaEdicion());
        verify(programaRepository).save(any(Programa.class));
        verify(pensumRepository).saveAll(anyList());
    }
}
