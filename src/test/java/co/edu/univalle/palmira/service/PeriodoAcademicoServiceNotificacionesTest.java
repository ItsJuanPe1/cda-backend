package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PeriodoAcademicoDto;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.service.notificacion.RecordatorioCoordinadorService;
import co.edu.univalle.palmira.service.notificacion.RecordatorioDocenteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * HU-038: enganches de los recordatorios y de la bitácora en el período académico
 * (HU-002 criterio 13, HU-003 criterio 8, HU-004 criterio 5). Datos ficticios.
 */
@ExtendWith(MockitoExtension.class)
class PeriodoAcademicoServiceNotificacionesTest {

    private static final Instant INICIO = Instant.parse("2027-01-20T13:00:00Z");
    private static final Instant FIN = Instant.parse("2027-02-03T22:00:00Z");

    @Mock
    private PeriodoAcademicoRepository repository;
    @Mock
    private BitacoraService bitacoraService;
    @Mock
    private RecordatorioDocenteService recordatorioDocenteService;
    @Mock
    private RecordatorioCoordinadorService recordatorioCoordinadorService;

    private PeriodoAcademicoService service;

    @BeforeEach
    void setUp() {
        service = new PeriodoAcademicoService(repository, bitacoraService, recordatorioDocenteService, recordatorioCoordinadorService);
    }

    private static PeriodoAcademicoDto.Request request(Instant inicio, Instant fin) {
        return new PeriodoAcademicoDto.Request("2027-1 Prueba", "RES-PRUEBA-1", LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 6, 30),
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10),
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 10),
                inicio, fin, null, null, null, null);
    }

    private static PeriodoAcademico vigente(Long id) {
        return PeriodoAcademico.builder().id(id).nombre("2027-1 Prueba").numeroResolucion("RES-PRUEBA-1")
                .fechaResolucion(LocalDate.of(2026, 11, 1))
                .fechaInicioSemestre(LocalDate.of(2027, 2, 1)).fechaFinSemestre(LocalDate.of(2027, 6, 30))
                .fechaAdicionInicio(LocalDate.of(2027, 2, 1)).fechaAdicionFin(LocalDate.of(2027, 2, 10))
                .fechaCancelacionInicio(LocalDate.of(2027, 3, 1)).fechaCancelacionFin(LocalDate.of(2027, 3, 10))
                .plazoDocenteInicio(INICIO).plazoDocenteFin(FIN)
                .vigente(true).estado("VIGENTE").build();
    }

    private void guardarConId(long id) {
        when(repository.save(any())).thenAnswer(inv -> {
            PeriodoAcademico p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(id);
            }
            return p;
        });
    }

    @Test
    @DisplayName("HU-002 criterio 13: al crear el período se programan los recordatorios y se registra en la bitácora")
    void hu002CrearProgramaRecordatorios() {
        when(repository.findFirstByVigenteTrue()).thenReturn(Optional.empty());
        guardarConId(8L);

        service.crear(request(INICIO, FIN));

        ArgumentCaptor<PeriodoAcademico> guardado = ArgumentCaptor.forClass(PeriodoAcademico.class);
        verify(recordatorioDocenteService).programarAlCrear(guardado.capture());
        assertEquals(8L, guardado.getValue().getId());
        verify(bitacoraService).registrar(eq(AccionBitacora.CREAR), eq(PeriodoAcademicoService.ENTIDAD_PERIODO),
                eq("8"), eq(8L), isNull(), any());
        verify(recordatorioDocenteService, never()).cancelarPendientes(any(), any());
        verify(recordatorioCoordinadorService).programarAlCrear(guardado.getValue());
    }

    @Test
    @DisplayName("HU-039 (HU-003 criterio 8): al editar se pasa el plazo de coordinadores anterior para avisar y reprogramar")
    void hu039ActualizarPasaPlazoCoordinadorAnterior() {
        Instant coordInicio = Instant.parse("2027-02-15T13:00:00Z");
        Instant coordFin = Instant.parse("2027-02-28T22:00:00Z");
        PeriodoAcademico actual = vigente(8L);
        actual.setPlazoCoordinadorInicio(coordInicio);
        actual.setPlazoCoordinadorFin(coordFin);
        when(repository.findById(8L)).thenReturn(Optional.of(actual));
        guardarConId(8L);

        service.actualizar(8L, request(INICIO, FIN));

        verify(recordatorioCoordinadorService).alCambiarPlazo(any(PeriodoAcademico.class), eq(coordInicio), eq(coordFin));
    }

    @Test
    @DisplayName("Criterio 7: al crear un período, el anterior pasa a historial y se cancelan sus pendientes")
    void criterio7CrearCancelaPendientesDelAnterior() {
        when(repository.findFirstByVigenteTrue()).thenReturn(Optional.of(vigente(3L)));
        guardarConId(8L);

        service.crear(request(INICIO, FIN));

        InOrder orden = inOrder(recordatorioDocenteService, repository);
        orden.verify(recordatorioDocenteService).cancelarPendientes(3L, RecordatorioDocenteService.MOTIVO_PERIODO_HISTORIAL);
        orden.verify(repository).marcarVigenteComoHistorial();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> despues = ArgumentCaptor.forClass(Map.class);
        verify(bitacoraService).registrar(eq(AccionBitacora.EDITAR), eq(PeriodoAcademicoService.ENTIDAD_PERIODO),
                eq("3"), eq(3L), any(), despues.capture());
        assertEquals("HISTORIAL", despues.getValue().get("estado"));
    }

    @Test
    @DisplayName("HU-003 criterio 8: al cambiar el plazo docente se envía la actualización y se reprograma")
    void hu003ActualizarConCambioDePlazo() {
        when(repository.findById(8L)).thenReturn(Optional.of(vigente(8L)));
        guardarConId(8L);
        Instant nuevoFin = FIN.plusSeconds(86400 * 5);

        service.actualizar(8L, request(INICIO, nuevoFin));

        verify(recordatorioDocenteService).alCambiarPlazo(any(PeriodoAcademico.class), eq(INICIO), eq(FIN));
        verify(bitacoraService).registrar(eq(AccionBitacora.EDITAR), eq(PeriodoAcademicoService.ENTIDAD_PERIODO),
                eq("8"), eq(8L), any(), any());
    }

    @Test
    @DisplayName("HU-003: si no cambia ningún dato, no se escribe en la bitácora")
    void hu003ActualizarSinCambios() {
        when(repository.findById(8L)).thenReturn(Optional.of(vigente(8L)));
        guardarConId(8L);

        service.actualizar(8L, request(INICIO, FIN));

        verify(bitacoraService, never()).registrar(any(), any(), any(), any(), any(), any());
        verify(recordatorioDocenteService).alCambiarPlazo(any(PeriodoAcademico.class), eq(INICIO), eq(FIN));
    }

    @Test
    @DisplayName("HU-004 criterio 5: al desactivar el período se cancelan los recordatorios pendientes")
    void hu004DesactivarCancelaPendientes() {
        when(repository.findById(8L)).thenReturn(Optional.of(vigente(8L)));

        service.desactivar(8L);

        verify(recordatorioDocenteService).cancelarPendientes(8L, RecordatorioDocenteService.MOTIVO_PERIODO_DESACTIVADO);
        verify(bitacoraService).registrar(eq(AccionBitacora.DESACTIVAR), eq(PeriodoAcademicoService.ENTIDAD_PERIODO),
                eq("8"), eq(8L), any(), any());
    }
}
