package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.entity.AccesoExtraordinarioDocente;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Acceso extraordinario: edición de disponibilidad con la ventana general cerrada. */
@ExtendWith(MockitoExtension.class)
class DisponibilidadDocenteAccesoExtraordinarioTest {

    private static final String EMAIL = "felipe@correounivalle.edu.co";

    @Mock DisponibilidadDocenteRepository disponibilidadRepository;
    @Mock PeriodoAcademicoRepository periodoRepository;
    @Mock DocenteRepository docenteRepository;
    @Mock AccesoExtraordinarioService accesoExtraordinarioService;

    DisponibilidadDocenteService service;
    Docente docente;
    PeriodoAcademico periodo;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadDocenteService(disponibilidadRepository, periodoRepository,
                docenteRepository, accesoExtraordinarioService);
        docente = Docente.builder().id(42L).estado(true).correoInstitucional(EMAIL).build();
        periodo = PeriodoAcademico.builder().id(7L)
                .plazoDocenteInicio(Instant.now().minusSeconds(7200))
                .plazoDocenteFin(Instant.now().minusSeconds(3600))   // ventana general CERRADA
                .build();
        lenient().when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.of(periodo));
        lenient().when(docenteRepository.findByCorreoInstitucionalWithDetails(EMAIL))
                .thenReturn(Optional.of(docente));
    }

    private Instant conAccesoVigenteHasta() {
        Instant fin = Instant.now().plusSeconds(1800);
        AccesoExtraordinarioDocente acceso = AccesoExtraordinarioDocente.builder()
                .id(1L).docente(docente).fechaInicio(Instant.now().minusSeconds(60)).fechaFin(fin)
                .motivo("Caso puntual").otorgadoPor("admin@correounivalle.edu.co").build();
        when(accesoExtraordinarioService.vigente(eq(42L), any(Instant.class))).thenReturn(Optional.of(acceso));
        return fin;
    }

    @Test
    void consultaConVentanaCerradaYAccesoVigente_devuelveHabilitadoYElFinDelAcceso() {
        Instant fin = conAccesoVigenteHasta();
        when(disponibilidadRepository.findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(42L, 7L))
                .thenReturn(List.of());

        DisponibilidadDocenteDto.Response r = service.consultar(EMAIL);

        assertEquals("HABILITADO", r.estado());
        assertEquals("Acceso extraordinario habilitado", r.mensaje());
        assertEquals(fin, r.accesoExtraordinarioFin());
    }

    @Test
    void consultaConVentanaCerradaSinAcceso_devuelveFueraDePlazo() {
        when(disponibilidadRepository.findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(42L, 7L))
                .thenReturn(List.of());

        DisponibilidadDocenteDto.Response r = service.consultar(EMAIL);

        assertEquals("FUERA_DE_PLAZO", r.estado());
        assertNull(r.accesoExtraordinarioFin());
    }

    @Test
    void registraConVentanaCerradaYAccesoVigente() {
        conAccesoVigenteHasta();
        when(disponibilidadRepository.findCruces(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(disponibilidadRepository.save(any(DisponibilidadDocente.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DisponibilidadDocenteDto.Item item = service.registrar(EMAIL,
                new DisponibilidadDocenteDto.Request(2, LocalTime.of(8, 0), LocalTime.of(9, 0)));

        assertEquals(2, item.dia());
        verify(disponibilidadRepository).save(any(DisponibilidadDocente.class));
    }

    @Test
    void actualizarTodoConVentanaCerradaYAccesoVigente() {
        conAccesoVigenteHasta();
        when(disponibilidadRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<DisponibilidadDocenteDto.Item> r = service.actualizarTodo(EMAIL,
                List.of(new DisponibilidadDocenteDto.Request(1, LocalTime.of(7, 0), LocalTime.of(9, 0))));

        assertEquals(1, r.size());
        verify(disponibilidadRepository).deleteAllByDocenteIdAndPeriodoId(42L, 7L);
    }

    @Test
    void ventanaCerradaSinAcceso_registrarSigueRechazando() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.registrar(EMAIL,
                new DisponibilidadDocenteDto.Request(2, LocalTime.of(8, 0), LocalTime.of(9, 0))));

        assertEquals("Fuera del período habilitado", ex.getMessage());
        verify(disponibilidadRepository, never()).save(any());
    }

    @Test
    void ventanaCerradaSinAcceso_actualizarTodoSigueRechazando() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.actualizarTodo(EMAIL, List.of()));

        assertEquals("Fuera del período habilitado", ex.getMessage());
        verify(disponibilidadRepository, never()).deleteAllByDocenteIdAndPeriodoId(any(), any());
    }

    @Test
    void conLaVentanaGeneralAbierta_noSeConsultaElAccesoExtraordinario() {
        periodo.setPlazoDocenteInicio(Instant.now().minusSeconds(3600));
        periodo.setPlazoDocenteFin(Instant.now().plusSeconds(3600));
        when(disponibilidadRepository.findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(42L, 7L))
                .thenReturn(List.of());
        when(disponibilidadRepository.findCruces(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(disponibilidadRepository.save(any(DisponibilidadDocente.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertEquals("HABILITADO", service.consultar(EMAIL).estado());
        service.registrar(EMAIL, new DisponibilidadDocenteDto.Request(2, LocalTime.of(8, 0), LocalTime.of(9, 0)));

        verifyNoInteractions(accesoExtraordinarioService);
    }
}