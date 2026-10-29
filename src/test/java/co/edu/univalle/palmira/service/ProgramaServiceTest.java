package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramaDto;
import jakarta.persistence.EntityManager;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * HU-040: crear y editar programas académicos. Datos de prueba ficticios.
 */
@ExtendWith(MockitoExtension.class)
class ProgramaServiceTest {

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private PensumAcademicoRepository pensumAcademicoRepository;

    @Mock
    private CoordinadorRepository coordinadorRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ProgramaService programaService;

    private Programa existente;

    @BeforeEach
    void setUp() {
        existente = Programa.builder()
                .codeProgram("9001")
                .nameProgram("Programa Prueba")
                .jornada(Jornada.DIURNA)
                .numeroSemestre(10)
                .ingreso(TipoIngreso.SEMESTRAL)
                .estado(EstadoPrograma.ACTIVO)
                .idFacultadPrograma(1)
                .build();
    }

    private ProgramaDto.CreateRequest crear(String jornada, Integer diurna, Integer nocturna, String estado) {
        return new ProgramaDto.CreateRequest(" 9002 ", "  Programa Nuevo  ", jornada, diurna, nocturna, "ANUAL", estado, 1);
    }

    private ProgramaDto.UpdateRequest editar(String code, String nombre, String jornada, Integer diurna, Integer nocturna, String estado) {
        return new ProgramaDto.UpdateRequest(code, nombre, jornada, diurna, nocturna, null, estado, null);
    }

    private void guardarDevuelveLoMismo() {
        when(programaRepository.save(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // Criterio 1 y 3

    @Test
    @DisplayName("Criterio 1: crea el programa con código, nombre, jornada, duración, ingreso y estado")
    void criterio1_creaProgramaConTodosLosCampos() {
        when(programaRepository.existsById("9002")).thenReturn(false);
        guardarDevuelveLoMismo();

        ProgramaDto.Response r = programaService.create(crear("AMBAS", 10, 11, null));

        assertEquals("9002", r.codeProgram());
        assertEquals("Programa Nuevo", r.nameProgram());
        assertEquals(Jornada.AMBAS, r.jornada());
        assertEquals(10, r.numeroSemestre());
        assertEquals(11, r.semestresNocturna());
        assertEquals(TipoIngreso.ANUAL, r.ingreso());
        assertEquals(EstadoPrograma.ACTIVO, r.estado(), "Sin estado, el programa se crea activo");
    }

    @Test
    @DisplayName("Criterio 3: un programa nuevo no tiene malla ni cohortes")
    void criterio3_programaNuevoSinMalla() {
        when(programaRepository.existsById("9002")).thenReturn(false);
        guardarDevuelveLoMismo();

        ProgramaDto.Response r = programaService.create(crear("DIURNA", 6, null, "ACTIVO"));

        assertEquals(0L, r.totalAsignaturas());
        verifyNoInteractions(pensumAcademicoRepository);
    }

    // Criterio 2: obligatoriedad y unicidad

    @Test
    @DisplayName("Criterio 2: código repetido responde «Ya existe un programa con este código»")
    void criterio2_codigoRepetido() {
        when(programaRepository.existsById("9002")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> programaService.create(crear("DIURNA", 6, null, null)));

        assertEquals("Ya existe un programa con este código", ex.getMessage());
        verify(programaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criterio 2: jornada diurna sin duración diurna no se guarda")
    void criterio2_diurnaSinDuracion() {
        when(programaRepository.existsById("9002")).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> programaService.create(crear("DIURNA", null, null, null)));

        assertEquals(ProgramaDto.MSG_DURACION_DIURNA, ex.getMessage());
        verify(programaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criterio 2: jornada ambas sin duración nocturna no se guarda")
    void criterio2_ambasSinDuracionNocturna() {
        when(programaRepository.existsById("9002")).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> programaService.create(crear("AMBAS", 10, null, null)));

        assertEquals(ProgramaDto.MSG_DURACION_NOCTURNA, ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 1: en jornada nocturna se guarda solo la duración nocturna")
    void criterio1_nocturnaGuardaSoloDuracionNocturna() {
        when(programaRepository.existsById("9002")).thenReturn(false);
        guardarDevuelveLoMismo();

        ProgramaDto.Response r = programaService.create(crear("NOCTURNA", 10, 11, null));

        assertNull(r.numeroSemestre());
        assertEquals(11, r.semestresNocturna());
    }

    // Criterio 4: editar mantiene mallas, cohortes y coordinador

    @Test
    @DisplayName("Criterio 4: editar actualiza los datos del programa y conserva su malla")
    void criterio4_editarConservaMalla() {
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
        guardarDevuelveLoMismo();
        when(pensumAcademicoRepository.countByIdCodeProgram("9001")).thenReturn(40L);

        ProgramaDto.Response r = programaService.update("9001",
                editar(null, "Programa Renombrado", "AMBAS", null, 11, null));

        assertEquals("9001", r.codeProgram());
        assertEquals("Programa Renombrado", r.nameProgram());
        assertEquals(Jornada.AMBAS, r.jornada());
        assertEquals(10, r.numeroSemestre(), "Conserva la duración diurna que no se envió");
        assertEquals(11, r.semestresNocturna());
        assertEquals(40L, r.totalAsignaturas(), "La malla sigue asociada");
        verify(pensumAcademicoRepository, never()).deleteAll();
    }

    @Test
    @DisplayName("Editar sin enviar número de resolución conserva el existente")
    void editarSinNumeroConservaNumeroResolucion() {
        existente.setNumeroResolucion("RES-ORIGINAL");
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
        guardarDevuelveLoMismo();

        ProgramaDto.Response r = programaService.update("9001",
                editar(null, "Programa Renombrado", null, null, null, null));

        assertEquals("RES-ORIGINAL", r.numeroResolucion());
    }

    @Test
    @DisplayName("Editar con número de resolución lo actualiza con trim")
    void editarActualizaNumeroResolucionConTrim() {
        existente.setNumeroResolucion("RES-ORIGINAL");
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
        guardarDevuelveLoMismo();

        ProgramaDto.Response r = programaService.update("9001", new ProgramaDto.UpdateRequest(
                null, null, null, null, null, null, null, null, null, "  RES-NUEVA  "));

        assertEquals("RES-NUEVA", r.numeroResolucion());
    }

    @Test
    @DisplayName("Criterio 4: el código del programa no se puede modificar")
    void criterio4_codigoNoEditable() {
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> programaService.update("9001", editar("9009", null, null, null, null, null)));

        assertEquals("El código del programa no se puede modificar", ex.getMessage());
        verify(programaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criterio 4: al pasar a diurna se vacía la duración nocturna")
    void criterio4_pasarADiurnaVaciaNocturna() {
        existente.setJornada(Jornada.AMBAS);
        existente.setSemestresNocturna(11);
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
        guardarDevuelveLoMismo();

        ProgramaDto.Response r = programaService.update("9001", editar(null, null, "DIURNA", null, null, null));

        assertEquals(10, r.numeroSemestre());
        assertNull(r.semestresNocturna());
    }

    @Test
    @DisplayName("Criterio 2: al pasar a ambas sin duración nocturna no se guarda")
    void criterio2_pasarAAmbasSinNocturna() {
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> programaService.update("9001", editar(null, null, "AMBAS", null, null, null)));

        assertEquals(ProgramaDto.MSG_DURACION_NOCTURNA, ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 4: reactivar un programa enviando estado ACTIVO")
    void criterio4_reactivar() {
        existente.setEstado(EstadoPrograma.INACTIVO);
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
        guardarDevuelveLoMismo();

        ProgramaDto.Response r = programaService.update("9001", editar(null, null, null, null, null, "ACTIVO"));

        assertEquals(EstadoPrograma.ACTIVO, r.estado());
    }

    @Test
    @DisplayName("Editar un programa que no existe responde «Programa no encontrado»")
    void editarInexistente() {
        when(programaRepository.findById("9999")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> programaService.update("9999", editar(null, "X", null, null, null, null)));

        assertEquals("Programa no encontrado", ex.getMessage());
    }

    //@Test
    //@DisplayName("Editar intercambia coordinadores sin dejar un programa sin asignación")
    //void editarIntercambiaCoordinadores() {
    //   Programa anteriorDelElegido = Programa.builder()
    //           .codeProgram("9002").nameProgram("Programa Dos")
    //           .jornada(Jornada.DIURNA).numeroSemestre(10)
    //            .ingreso(TipoIngreso.SEMESTRAL).estado(EstadoPrograma.ACTIVO).build();
    //     Coordinador anterior = Coordinador.builder().id(10).identificacion("DOC-10")
    //            .nombreCompleto("Coordinador actual").programa(existente).activo(true).build();
    //    Coordinador elegido = Coordinador.builder().id(20).identificacion("DOC-20")
    //           .nombreCompleto("Coordinador nuevo").programa(anteriorDelElegido).activo(true).build();
//
    //   when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
    //   when(programaRepository.findAllByCodeProgramInForUpdate(anyCollection()))
    //           .thenReturn(List.of(existente, anteriorDelElegido));
    //   when(coordinadorRepository.findByIdWithDetails(20)).thenReturn(Optional.of(elegido));
    //   when(coordinadorRepository.findByProgramaCodeProgram("9001")).thenReturn(List.of(anterior));
    //   when(coordinadorRepository.findByProgramaCodeProgram("9002")).thenReturn(List.of(elegido));
    //   when(coordinadorRepository.findAllByIdsForUpdate(anyCollection()))
    //           .thenReturn(List.of(anterior, elegido));
    //   when(coordinadorRepository.saveAndFlush(any(Coordinador.class)))
    //          .thenAnswer(inv -> inv.getArgument(0));
    //  guardarDevuelveLoMismo();
    //  when(pensumAcademicoRepository.countByIdCodeProgram("9001")).thenReturn(0L);
    //
    //   ProgramaDto.UpdateRequest request = new ProgramaDto.UpdateRequest(
    //            null, "Programa actualizado", null, null, null, null, null, null,
    //           null, null, 20);
    //   programaService.update("9001", request);
//
    //   assertEquals("9002", anterior.getPrograma().getCodeProgram());
    //   assertEquals("9001", elegido.getPrograma().getCodeProgram());
    //  verify(coordinadorRepository, times(3)).saveAndFlush(any(Coordinador.class));
        //}

    //@Test
    //@DisplayName("No intercambia si el programa destino no tiene coordinador y el elegido dejaría otro huérfano")
    //void intercambioRechazadoSiDejariaProgramaHuerfano() {
        //   Programa anteriorDelElegido = Programa.builder()
        //          .codeProgram("9002").nameProgram("Programa Dos")
        //          .jornada(Jornada.DIURNA).numeroSemestre(10)
        //          .ingreso(TipoIngreso.SEMESTRAL).estado(EstadoPrograma.ACTIVO).build();
        //    Coordinador elegido = Coordinador.builder().id(20).identificacion("DOC-20")
        //            .nombreCompleto("Coordinador nuevo").programa(anteriorDelElegido).activo(true).build();

        //    when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
        //    when(programaRepository.findAllByCodeProgramInForUpdate(anyCollection()))
        //            .thenReturn(List.of(existente, anteriorDelElegido));
        //    when(coordinadorRepository.findByIdWithDetails(20)).thenReturn(Optional.of(elegido));
        //    when(coordinadorRepository.findByProgramaCodeProgram("9001")).thenReturn(List.of());
        //    when(coordinadorRepository.findByProgramaCodeProgram("9002")).thenReturn(List.of(elegido));
        //     when(coordinadorRepository.findAllByIdsForUpdate(anyCollection())).thenReturn(List.of(elegido));
//
        //      ProgramaDto.UpdateRequest request = new ProgramaDto.UpdateRequest(
                //              null, null, null, null, null, null, null, null, null, null, 20);
        //     BadRequestException ex = assertThrows(BadRequestException.class,
                //           () -> programaService.update("9001", request));
//
        //   assertTrue(ex.getMessage().contains("no tiene un coordinador asignado"));
        //  verify(coordinadorRepository, never()).saveAndFlush(any());
        //}

    // Criterio 5

    @Test
    @DisplayName("Criterio 5: el administrador ve todos los programas, activos e inactivos")
    void criterio5_listaActivosEInactivos() {
        Programa inactivo = Programa.builder()
                .codeProgram("9003").nameProgram("Programa Inactivo")
                .numeroSemestre(6).estado(EstadoPrograma.INACTIVO).build();
        when(programaRepository.findAllByOrderByNameProgramAsc()).thenReturn(List.of(existente, inactivo));

        List<ProgramaDto.Response> lista = programaService.getAll();

        assertEquals(2, lista.size());
        assertTrue(lista.stream().anyMatch(p -> p.estado() == EstadoPrograma.INACTIVO));
    }

    // RN-13: desactivar en lugar de eliminar

    @Test
    @DisplayName("RN-13: desactivar no borra el programa, cambia su estado a INACTIVO")
    void desactivarNoBorra() {
        when(programaRepository.findById("9001")).thenReturn(Optional.of(existente));
        ArgumentCaptor<Programa> captor = ArgumentCaptor.forClass(Programa.class);

        programaService.desactivar("9001");

        verify(programaRepository).save(captor.capture());
        assertEquals(EstadoPrograma.INACTIVO, captor.getValue().getEstado());
        verify(programaRepository, never()).deleteById(any());
        verify(programaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Desactivar un programa que no existe responde «Programa no encontrado»")
    void desactivarInexistente() {
        when(programaRepository.findById("9999")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> programaService.desactivar("9999"));
    }
}
