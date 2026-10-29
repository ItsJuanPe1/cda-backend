package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramaCoordinadorDto;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HU-037: ver programas académicos y sus coordinadores. Datos de prueba ficticios.
 */
@ExtendWith(MockitoExtension.class)
class ProgramaCoordinadorServiceTest {

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private CoordinadorRepository coordinadorRepository;

    @InjectMocks
    private ProgramaCoordinadorService service;

    private Programa software;
    private Programa administracion;
    private Coordinador coordSoftware;

    @BeforeEach
    void setUp() {
        software = programa("9001", "Tecnología Ficticia en Software", Jornada.DIURNA);
        administracion = programa("9002", "Administración Ficticia", Jornada.AMBAS);
        coordSoftware = Coordinador.builder()
                .id(7)
                .nombreCompleto("Coordinación Ficticia Software")
                .correoInstitucional("ficticio.software@correounivalle.edu.co")
                .programa(software)
                .activo(true)
                .build();
    }

    private static Programa programa(String code, String nombre, Jornada jornada) {
        return Programa.builder()
                .codeProgram(code)
                .nameProgram(nombre)
                .jornada(jornada)
                .estado(EstadoPrograma.ACTIVO)
                .build();
    }

    private void dado(List<Programa> programasActivos, List<Coordinador> coordinadoresActivos) {
        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(programasActivos);
        when(coordinadorRepository.findActivosConPrograma()).thenReturn(coordinadoresActivos);
    }

    @Test
    @DisplayName("Criterio 1: el programa con coordinador activo muestra su coordinador")
    void criterio1ProgramaConCoordinador() {
        dado(List.of(software), List.of(coordSoftware));

        List<ProgramaCoordinadorDto.Response> r = service.listar(null, false);

        assertEquals(1, r.size());
        assertTrue(r.get(0).tieneCoordinador());
        assertEquals(7, r.get(0).coordinador().id());
        assertEquals("Coordinación Ficticia Software", r.get(0).coordinador().nombreCompleto());
        assertEquals("ficticio.software@correounivalle.edu.co", r.get(0).coordinador().correoInstitucional());
        assertEquals(Jornada.DIURNA, r.get(0).jornada());
    }

    @Test
    @DisplayName("Criterio 1: el programa sin coordinador sale «Sin coordinador»")
    void criterio1ProgramaSinCoordinador() {
        dado(List.of(administracion), List.of());

        List<ProgramaCoordinadorDto.Response> r = service.listar(null, false);

        assertEquals(1, r.size());
        assertFalse(r.get(0).tieneCoordinador());
        assertNull(r.get(0).coordinador());
    }

    @Test
    @DisplayName("Criterio 1: un coordinador desactivado no cuenta y el programa sale «Sin coordinador»")
    void criterio1CoordinadorDesactivadoNoCuenta() {
        // El repositorio solo devuelve coordinadores activos; el desactivado de software no llega
        dado(List.of(software), List.of());

        List<ProgramaCoordinadorDto.Response> r = service.listar(null, false);

        assertFalse(r.get(0).tieneCoordinador());
        assertNull(r.get(0).coordinador());
    }

    @Test
    @DisplayName("Criterio 1: solo se consultan los programas en estado ACTIVO")
    void criterio1SoloProgramasActivos() {
        dado(List.of(software), List.of(coordSoftware));

        service.listar(null, false);

        verify(programaRepository).findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO);
        verify(programaRepository, never()).findAllByOrderByNameProgramAsc();
    }

    @Test
    @DisplayName("Criterio 1: conserva el orden por nombre del repositorio")
    void criterio1ConservaOrden() {
        dado(List.of(administracion, software), List.of(coordSoftware));

        List<ProgramaCoordinadorDto.Response> r = service.listar(null, false);

        assertEquals(List.of("9002", "9001"), r.stream().map(ProgramaCoordinadorDto.Response::codeProgram).toList());
    }

    @Test
    @DisplayName("Criterio 2: busca por nombre del programa sin distinguir mayúsculas ni espacios de los extremos")
    void criterio2BuscaPorNombrePrograma() {
        dado(List.of(software, administracion), List.of(coordSoftware));

        List<ProgramaCoordinadorDto.Response> r = service.listar("  ADMINISTRACIÓN  ", false);

        assertEquals(List.of("9002"), r.stream().map(ProgramaCoordinadorDto.Response::codeProgram).toList());
    }

    @Test
    @DisplayName("Criterio 2: busca por código del programa")
    void criterio2BuscaPorCodigoPrograma() {
        dado(List.of(software, administracion), List.of(coordSoftware));

        List<ProgramaCoordinadorDto.Response> r = service.listar("9001", false);

        assertEquals(List.of("9001"), r.stream().map(ProgramaCoordinadorDto.Response::codeProgram).toList());
    }

    @Test
    @DisplayName("Criterio 2: busca por nombre del coordinador activo")
    void criterio2BuscaPorCoordinador() {
        dado(List.of(software, administracion), List.of(coordSoftware));

        List<ProgramaCoordinadorDto.Response> r = service.listar("coordinación ficticia", false);

        assertEquals(List.of("9001"), r.stream().map(ProgramaCoordinadorDto.Response::codeProgram).toList());
    }

    @Test
    @DisplayName("Criterio 2: un coordinador desactivado no aparece en la búsqueda")
    void criterio2CoordinadorDesactivadoNoSeBusca() {
        dado(List.of(software, administracion), List.of());

        List<ProgramaCoordinadorDto.Response> r = service.listar("coordinación ficticia", false);

        assertTrue(r.isEmpty());
    }

    @Test
    @DisplayName("Criterio 2: el filtro «Sin coordinador» deja solo los programas sin coordinador activo")
    void criterio2FiltraSinCoordinador() {
        dado(List.of(software, administracion), List.of(coordSoftware));

        List<ProgramaCoordinadorDto.Response> r = service.listar(null, true);

        assertEquals(List.of("9002"), r.stream().map(ProgramaCoordinadorDto.Response::codeProgram).toList());
    }

    @Test
    @DisplayName("Criterio 2: la búsqueda y el filtro «Sin coordinador» se combinan")
    void criterio2CombinaBusquedaYFiltro() {
        dado(List.of(software, administracion), List.of(coordSoftware));

        assertTrue(service.listar("software", true).isEmpty());
        assertEquals(1, service.listar("administración", true).size());
    }

    @Test
    @DisplayName("Criterio 2: una búsqueda vacía o solo con espacios no filtra")
    void criterio2BusquedaVaciaNoFiltra() {
        dado(List.of(software, administracion), List.of(coordSoftware));

        assertEquals(2, service.listar("   ", false).size());
    }

    @Test
    @DisplayName("Criterio 4: sin programas activos devuelve una lista vacía")
    void criterio4SinProgramas() {
        dado(List.of(), List.of());

        assertTrue(service.listar(null, false).isEmpty());
    }

    @Test
    @DisplayName("Criterio 4: sin coincidencias devuelve una lista vacía")
    void criterio4SinResultados() {
        dado(List.of(software, administracion), List.of(coordSoftware));

        assertTrue(service.listar("no existe", false).isEmpty());
    }
}
