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
        when(docenteRepository.findByCorreoInstitucionalWithDetails(EMAIL)).thenReturn(Optional.of(docente));
        lenient().when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.of(periodo));
    }

    @Test
    void consultaDevuelveEstadoHabilitadoYHorariosDelDocenteAutenticado() {
        when(disponibilidadRepository.findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(42L, 7L))
                .thenReturn(List.of());

        DisponibilidadDocenteDto.Response response = service.consultar(EMAIL);

        assertEquals("HABILITADO", response.estado());
        assertEquals("Registrar disponibilidad", response.mensaje());
        assertTrue(response.horarios().isEmpty());
    }

    @Test
    void consultaIndicaCuandoNoHayPeriodoAcademicoVigente() {
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
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(7, LocalTime.of(9, 0), LocalTime.of(10, 0))));

        assertEquals("El día debe estar entre lunes y sábado", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaHorasAntesDeLasSiete() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(1, LocalTime.of(6, 30), LocalTime.of(8, 0))));

        assertEquals("El horario debe estar entre las 07:00 y las 21:00", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaHoraFinPosteriorALasVeintiuna() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(6, LocalTime.of(20, 0), LocalTime.of(21, 30))));

        assertEquals("El horario debe estar entre las 07:00 y las 21:00", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaRegistrarCuandoElPlazoCerro() {
        periodo.setPlazoDocenteInicio(Instant.now().minusSeconds(7200));
        periodo.setPlazoDocenteFin(Instant.now().minusSeconds(3600));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(
                EMAIL, new DisponibilidadDocenteDto.Request(1, LocalTime.of(8, 0), LocalTime.of(9, 0))));

        assertEquals("Fuera del período habilitado", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void rechazaUnRangoQueSeCruzaConOtroRegistro() {
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
}
