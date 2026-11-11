package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.entity.DisponibilidadDocente;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.DisponibilidadDocenteRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisponibilidadDocenteServiceTest {
    private static final String EMAIL = "felipe@correounivalle.edu.co";

    @Mock
    private DisponibilidadDocenteRepository disponibilidadRepository;
    @Mock
    private PeriodoAcademicoRepository periodoRepository;
    @Mock
    private DocenteRepository docenteRepository;

    private DisponibilidadDocenteService service;
    private Docente docente;
    private PeriodoAcademico periodo;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadDocenteService(
                disponibilidadRepository, periodoRepository, docenteRepository);
        docente = Docente.builder().id(42L).estado(true).correoInstitucional(EMAIL).build();
        periodo = PeriodoAcademico.builder()
                .id(7L)
                .plazoDocenteInicio(Instant.now().minusSeconds(3600))
                .plazoDocenteFin(Instant.now().plusSeconds(3600))
                .build();
        lenient().when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.of(periodo));
    }

    /** Configura el docente autenticado solo en las pruebas que usan el flujo por correo. */
    private void stubDocenteAutenticado() {
        when(docenteRepository.findByCorreoInstitucionalWithDetails(EMAIL))
                .thenReturn(Optional.of(docente));
    }


    @Test
    void consultaPorIdAdministrativoUsaElIdDeDocentesYNoElCorreoDelAdministrador() {
        when(docenteRepository.findById(42L)).thenReturn(Optional.of(docente));
        when(disponibilidadRepository.findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(42L, 7L))
                .thenReturn(List.of());

        DisponibilidadDocenteDto.Response response = service.consultarPorDocenteId(42L);

        assertEquals("HABILITADO", response.estado());
        verify(docenteRepository).findById(42L);
        verify(disponibilidadRepository)
                .findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(42L, 7L);
        verify(docenteRepository, never()).findByCorreoInstitucionalWithDetails(any());
    }

    @Test
    void registraDisponibilidadAdministrativaEnElDocenteIndicadoPorId() {
        when(docenteRepository.findById(42L)).thenReturn(Optional.of(docente));
        when(disponibilidadRepository.findCruces(42L, 7L, 3,
                LocalTime.of(10, 0), LocalTime.of(11, 30))).thenReturn(List.of());
        when(disponibilidadRepository.save(any(DisponibilidadDocente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DisponibilidadDocenteDto.Item response = service.registrarPorDocenteId(
                42L, new DisponibilidadDocenteDto.Request(3, LocalTime.of(10, 0), LocalTime.of(11, 30)));

        assertEquals(3, response.dia());
        verify(disponibilidadRepository).save(argThat(d ->
                d.getDocente().getId().equals(42L) && d.getPeriodo().getId().equals(7L)));
        verify(docenteRepository, never()).findByCorreoInstitucionalWithDetails(any());
    }

    @Test
    void noPermiteGestionarPorIdUnDocenteInactivo() {
        docente.setEstado(false);
        when(docenteRepository.findById(42L)).thenReturn(Optional.of(docente));

        assertThrows(co.edu.univalle.palmira.exception.ResourceNotFoundException.class,
                () -> service.consultarPorDocenteId(42L));
        verify(disponibilidadRepository, never())
                .findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(any(), any());
    }

    @Test
    void consultaDevuelveEstadoHabilitadoYHorariosDelDocenteAutenticado() {
        stubDocenteAutenticado();
        when(disponibilidadRepository.findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(42L, 7L))
                .thenReturn(List.of());

        DisponibilidadDocenteDto.Response response = service.consultar(EMAIL);

        assertEquals("HABILITADO", response.estado());
        assertEquals("Registrar disponibilidad", response.mensaje());
        assertTrue(response.horarios().isEmpty());
    }

    @Test
    void consultaIndicaCuandoNoHayPeriodoAcademicoVigente() {
        stubDocenteAutenticado();
        when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.empty());

        DisponibilidadDocenteDto.Response response = service.consultar(EMAIL);

        assertEquals("SIN_PERIODO", response.estado());
        assertEquals("No hay un período académico vigente", response.mensaje());
        assertTrue(response.horarios().isEmpty());
        verify(disponibilidadRepository, never())
                .findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(any(), any());
    }

    @Test
    void registraRangoValidoYLoAsociaAlDocenteYPeriodoCorrectos() {
        stubDocenteAutenticado();
        when(disponibilidadRepository.findCruces(42L, 7L, 2,
                LocalTime.of(7, 30), LocalTime.of(9, 0))).thenReturn(List.of());
        when(disponibilidadRepository.save(any(DisponibilidadDocente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DisponibilidadDocenteDto.Item response = service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(2, LocalTime.of(7, 30), LocalTime.of(9, 0)));

        assertEquals(2, response.dia());
        assertEquals(LocalTime.of(7, 30), response.horaInicio());
        assertEquals(LocalTime.of(9, 0), response.horaFin());
        verify(disponibilidadRepository).save(argThat(d ->
                d.getDocente().getId().equals(42L) && d.getPeriodo().getId().equals(7L)));
    }

    @Test
    void rechazaDomingo() {
        stubDocenteAutenticado();
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(7, LocalTime.of(9, 0), LocalTime.of(10, 0))));

        assertEquals("El día debe estar entre lunes y sábado", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaHorasAntesDeLasSiete() {
        stubDocenteAutenticado();
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(1, LocalTime.of(6, 30), LocalTime.of(8, 0))));

        assertEquals("El horario debe estar entre las 07:00 y las 21:00", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaHoraFinPosteriorALasVeintiuna() {
        stubDocenteAutenticado();
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(6, LocalTime.of(20, 0), LocalTime.of(21, 30))));

        assertEquals("El horario debe estar entre las 07:00 y las 21:00", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaRegistrarCuandoElPlazoCerro() {
        stubDocenteAutenticado();
        periodo.setPlazoDocenteInicio(Instant.now().minusSeconds(7200));
        periodo.setPlazoDocenteFin(Instant.now().minusSeconds(3600));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(1, LocalTime.of(8, 0), LocalTime.of(9, 0))));

        assertEquals("Fuera del período habilitado", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaUnRangoQueSeCruzaConOtroRegistro() {
        stubDocenteAutenticado();
        DisponibilidadDocente existente = DisponibilidadDocente.builder()
                .id(99L).docente(docente).periodo(periodo).dia(2)
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(10, 0)).build();
        when(disponibilidadRepository.findCruces(42L, 7L, 2,
                LocalTime.of(9, 0), LocalTime.of(11, 0))).thenReturn(List.of(existente));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(2, LocalTime.of(9, 0), LocalTime.of(11, 0))));

        assertTrue(ex.getMessage().contains("El horario se cruza con otro ya registrado"));
        verify(disponibilidadRepository, never()).save(any());
    }

    /*
     * Prueba de usabilidad (2026-11-10): registro mínimo de 1 hora y sábado sin
     * disponibilidad de 19:00 a 21:00. Aplica al docente (HU-007/HU-008) y al
     * administrador (HU-051).
     */
    private static final String MENSAJE_MINIMO =
            "No se puede seleccionar 30 minutos, el registro mínimo es 1 hora";
    private static final String MENSAJE_FUERA_DE_RANGO = "Hora fuera del rango permitido";

    private void stubGuardadoDevuelveLaEntidad() {
        when(disponibilidadRepository.save(any(DisponibilidadDocente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rechazaUnBloqueDeTreintaMinutosPorqueElRegistroMinimoEsUnaHora() {
        stubDocenteAutenticado();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(2, LocalTime.of(8, 0), LocalTime.of(8, 30))));

        assertEquals(MENSAJE_MINIMO, ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void permiteUnBloqueDeExactamenteUnaHora() {
        stubDocenteAutenticado();
        stubGuardadoDevuelveLaEntidad();

        DisponibilidadDocenteDto.Item response = service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(2, LocalTime.of(8, 0), LocalTime.of(9, 0)));

        assertEquals(LocalTime.of(8, 0), response.horaInicio());
        assertEquals(LocalTime.of(9, 0), response.horaFin());
        verify(disponibilidadRepository).save(any(DisponibilidadDocente.class));
    }

    @Test
    void rechazaDisponibilidadElSabadoDeSieteANueveDeLaNoche() {
        stubDocenteAutenticado();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(6, LocalTime.of(19, 0), LocalTime.of(21, 0))));

        assertEquals(MENSAJE_FUERA_DE_RANGO, ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaUnRangoDelSabadoQueTerminaDespuesDeLasSieteDeLaNoche() {
        stubDocenteAutenticado();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(6, LocalTime.of(17, 0), LocalTime.of(19, 30))));

        assertEquals(MENSAJE_FUERA_DE_RANGO, ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void permiteElSabadoHastaLasSieteDeLaNoche() {
        stubDocenteAutenticado();
        stubGuardadoDevuelveLaEntidad();

        DisponibilidadDocenteDto.Item response = service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(6, LocalTime.of(17, 0), LocalTime.of(19, 0)));

        assertEquals(6, response.dia());
        assertEquals(LocalTime.of(19, 0), response.horaFin());
        verify(disponibilidadRepository).save(any(DisponibilidadDocente.class));
    }

    @Test
    void permiteOtrosDiasDeSieteANueveDeLaNoche() {
        stubDocenteAutenticado();
        stubGuardadoDevuelveLaEntidad();

        DisponibilidadDocenteDto.Item response = service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(5, LocalTime.of(19, 0), LocalTime.of(21, 0)));

        assertEquals(5, response.dia());
        assertEquals(LocalTime.of(21, 0), response.horaFin());
        verify(disponibilidadRepository).save(any(DisponibilidadDocente.class));
    }

    @Test
    void edicionCompletaRechazaUnIntervaloDeTreintaMinutosSinBorrarLoRegistrado() {
        stubDocenteAutenticado();
        List<DisponibilidadDocenteDto.Request> requests = List.of(
                new DisponibilidadDocenteDto.Request(1, LocalTime.of(7, 0), LocalTime.of(9, 0)),
                new DisponibilidadDocenteDto.Request(1, LocalTime.of(10, 0), LocalTime.of(10, 30)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.actualizarTodo(EMAIL, requests));

        assertEquals(MENSAJE_MINIMO, ex.getMessage());
        verify(disponibilidadRepository, never()).deleteAllByDocenteIdAndPeriodoId(any(), any());
        verify(disponibilidadRepository, never()).saveAll(any());
    }

    @Test
    void administradorTambienQuedaSujetoALasReglasDelSabadoYDelMinimo() {
        when(docenteRepository.findById(42L)).thenReturn(Optional.of(docente));

        BadRequestException sabado = assertThrows(BadRequestException.class,
                () -> service.registrarPorDocenteId(42L,
                        new DisponibilidadDocenteDto.Request(6, LocalTime.of(19, 0), LocalTime.of(21, 0))));
        BadRequestException minimo = assertThrows(BadRequestException.class,
                () -> service.registrarPorDocenteId(42L,
                        new DisponibilidadDocenteDto.Request(3, LocalTime.of(14, 0), LocalTime.of(14, 30))));

        assertEquals(MENSAJE_FUERA_DE_RANGO, sabado.getMessage());
        assertEquals(MENSAJE_MINIMO, minimo.getMessage());
        verify(disponibilidadRepository, never()).save(any());
        verify(docenteRepository, never()).findByCorreoInstitucionalWithDetails(any());
    }

    /*
     * Segunda ronda de usabilidad (2026-11-10): «Limpiar selección» con disponibilidad
     * registrada usa el PUT existente con una lista vacía (HU-007/HU-008 y HU-051).
     */

    @Test
    void limpiarSeleccionConListaVaciaEliminaLaDisponibilidadRegistradaDelDocente() {
        stubDocenteAutenticado();

        List<DisponibilidadDocenteDto.Item> resultado = service.actualizarTodo(EMAIL, List.of());

        assertTrue(resultado.isEmpty());
        verify(disponibilidadRepository).deleteAllByDocenteIdAndPeriodoId(42L, 7L);
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void administradorPuedeLimpiarLaDisponibilidadDeUnDocente() {
        when(docenteRepository.findById(42L)).thenReturn(Optional.of(docente));

        List<DisponibilidadDocenteDto.Item> resultado = service.actualizarTodoPorDocenteId(42L, List.of());

        assertTrue(resultado.isEmpty());
        verify(disponibilidadRepository).deleteAllByDocenteIdAndPeriodoId(42L, 7L);
        verify(disponibilidadRepository, never()).save(any());
        verify(docenteRepository, never()).findByCorreoInstitucionalWithDetails(any());
    }

    @Test
    void limpiarSeleccionRespetaElPlazoDocente() {
        stubDocenteAutenticado();
        periodo.setPlazoDocenteInicio(Instant.now().minusSeconds(7200));
        periodo.setPlazoDocenteFin(Instant.now().minusSeconds(3600));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.actualizarTodo(EMAIL, List.of()));

        assertEquals("Fuera del período habilitado", ex.getMessage());
        verify(disponibilidadRepository, never()).deleteAllByDocenteIdAndPeriodoId(any(), any());
        verify(disponibilidadRepository, never()).saveAll(any());
    }
}
