package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.AccesoExtraordinarioDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.AccesoExtraordinarioDocente;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.repository.AccesoExtraordinarioRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccesoExtraordinarioServiceTest {

    static final Long DOCENTE = 42L;
    static final String ADMIN = "admin@correounivalle.edu.co";

    @Mock AccesoExtraordinarioRepository accesoRepository;
    @Mock DocenteRepository docenteRepository;
    @Mock PeriodoAcademicoRepository periodoRepository;
    @Mock BitacoraService bitacoraService;

    AccesoExtraordinarioService service;
    Docente docente;

    @BeforeEach
    void setUp() {
        service = new AccesoExtraordinarioService(accesoRepository, docenteRepository,
                periodoRepository, bitacoraService);
        docente = Docente.builder().id(DOCENTE).nombres("Ana").apellidos("Pérez").estado(true).build();
        lenient().when(docenteRepository.findByIdForUpdate(DOCENTE)).thenReturn(Optional.of(docente));
        lenient().when(docenteRepository.existsById(DOCENTE)).thenReturn(true);
        lenient().when(accesoRepository.findByDocenteIdAndRevocadoEnIsNullOrderByFechaFinDesc(DOCENTE))
                .thenReturn(List.of());
        lenient().when(accesoRepository.save(any(AccesoExtraordinarioDocente.class)))
                .thenAnswer(inv -> {
                    AccesoExtraordinarioDocente a = inv.getArgument(0);
                    if (a.getId() == null) {
                        a.setId(5L);
                    }
                    return a;
                });
    }

    private AccesoExtraordinarioDto.Request pedido(Instant fin, String motivo) {
        return new AccesoExtraordinarioDto.Request(fin, motivo);
    }

    private AccesoExtraordinarioDocente acceso(Instant inicio, Instant fin) {
        return AccesoExtraordinarioDocente.builder().id(9L).docente(docente)
                .fechaInicio(inicio).fechaFin(fin).motivo("Caso puntual").otorgadoPor(ADMIN).build();
    }

    private void conAccesoVigente() {
        AccesoExtraordinarioDocente vigente = acceso(Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600));
        when(accesoRepository.findByDocenteIdAndRevocadoEnIsNullOrderByFechaFinDesc(DOCENTE))
                .thenReturn(List.of(vigente));
    }

    // ---------- otorgar ----------

    @Test
    void otorgar_guardaYRegistraBitacoraCrearConMotivoFechasYDocente() {
        Instant fin = Instant.now().plus(2, ChronoUnit.DAYS);
        PeriodoAcademico periodo = mock(PeriodoAcademico.class);
        when(periodo.getId()).thenReturn(9L);
        when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.of(periodo));

        AccesoExtraordinarioDto.Respuesta r = service.otorgar(DOCENTE, ADMIN, pedido(fin, "  Cierre de notas  "));

        assertEquals(DOCENTE, r.docenteId());
        assertEquals("Cierre de notas", r.motivo());
        assertEquals(fin, r.fechaFin());
        assertEquals(ADMIN, r.otorgadoPor());

        ArgumentCaptor<Object> nuevo = ArgumentCaptor.forClass(Object.class);
        verify(bitacoraService).registrar(eq(AccionBitacora.CREAR), eq("ACCESO_EXTRAORDINARIO"),
                eq("5"), eq(9L), isNull(), nuevo.capture());
        Map<?, ?> datos = (Map<?, ?>) nuevo.getValue();
        assertEquals(DOCENTE, datos.get("docente_id"));
        assertEquals("Ana Pérez", datos.get("docente"));
        assertEquals("Cierre de notas", datos.get("motivo"));
        assertEquals(fin.toString(), datos.get("fecha_fin"));
        assertNotNull(datos.get("fecha_inicio"));
    }

    @Test
    void otorgar_motivoVacioOSoloEspacios_seRechaza() {
        Instant fin = Instant.now().plusSeconds(3600);
        assertEquals(AccesoExtraordinarioService.MSG_MOTIVO, assertThrows(BadRequestException.class,
                () -> service.otorgar(DOCENTE, ADMIN, pedido(fin, ""))).getMessage());
        assertEquals(AccesoExtraordinarioService.MSG_MOTIVO, assertThrows(BadRequestException.class,
                () -> service.otorgar(DOCENTE, ADMIN, pedido(fin, "   "))).getMessage());
        assertThrows(BadRequestException.class, () -> service.otorgar(DOCENTE, ADMIN, pedido(fin, null)));
        verify(accesoRepository, never()).save(any());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    void otorgar_motivoDeMasDe500Caracteres_seRechaza() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.otorgar(
                DOCENTE, ADMIN, pedido(Instant.now().plusSeconds(3600), "x".repeat(501))));
        assertEquals(AccesoExtraordinarioService.MSG_MOTIVO_LARGO, ex.getMessage());
    }

    @Test
    void otorgar_finNuloOPedidoNulo_seRechaza() {
        assertThrows(BadRequestException.class, () -> service.otorgar(DOCENTE, ADMIN, pedido(null, "Motivo")));
        assertThrows(BadRequestException.class, () -> service.otorgar(DOCENTE, ADMIN, null));
    }

    @Test
    void otorgar_finEnElPasadoOAhora_seRechaza() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.otorgar(
                DOCENTE, ADMIN, pedido(Instant.now().minusSeconds(60), "Motivo")));
        assertEquals(AccesoExtraordinarioService.MSG_FIN_PASADO, ex.getMessage());
        assertThrows(BadRequestException.class, () -> service.otorgar(
                DOCENTE, ADMIN, pedido(Instant.now(), "Motivo")));
        verify(accesoRepository, never()).save(any());
    }

    @Test
    void otorgar_exactamente30Dias_pasa_y30DiasMasUnMinuto_seRechaza() {
        assertDoesNotThrow(() -> service.otorgar(DOCENTE, ADMIN,
                pedido(Instant.now().plus(30, ChronoUnit.DAYS), "Motivo")));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.otorgar(
                DOCENTE, ADMIN, pedido(Instant.now().plus(30, ChronoUnit.DAYS).plusSeconds(60), "Motivo")));
        assertEquals(AccesoExtraordinarioService.MSG_FIN_LEJANO, ex.getMessage());
    }

    @Test
    void otorgar_docenteInexistenteOInactivo_lanza404() {
        when(docenteRepository.findByIdForUpdate(77L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.otorgar(
                77L, ADMIN, pedido(Instant.now().plusSeconds(3600), "Motivo")));

        docente.setEstado(false);
        assertThrows(ResourceNotFoundException.class, () -> service.otorgar(
                DOCENTE, ADMIN, pedido(Instant.now().plusSeconds(3600), "Motivo")));
        verify(accesoRepository, never()).save(any());
    }

    @Test
    void otorgar_conOtroVigente_lanza409SinGuardarNiRegistrarBitacora() {
        conAccesoVigente();

        ConflictException ex = assertThrows(ConflictException.class, () -> service.otorgar(
                DOCENTE, ADMIN, pedido(Instant.now().plusSeconds(7200), "Otro motivo")));

        assertEquals(AccesoExtraordinarioService.MSG_YA_VIGENTE, ex.getMessage());
        verify(accesoRepository, never()).save(any());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    void otorgar_conUnoAnteriorYaVencido_permiteOtorgarOtro() {
        AccesoExtraordinarioDocente vencido = acceso(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        when(accesoRepository.findByDocenteIdAndRevocadoEnIsNullOrderByFechaFinDesc(DOCENTE))
                .thenReturn(List.of(vencido));

        assertDoesNotThrow(() -> service.otorgar(DOCENTE, ADMIN,
                pedido(Instant.now().plusSeconds(3600), "Nuevo caso")));
        verify(accesoRepository).save(any(AccesoExtraordinarioDocente.class));
    }

    @Test
    void otorgar_sinCorreoDelAdmin_lanzaAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> service.otorgar(
                DOCENTE, null, pedido(Instant.now().plusSeconds(3600), "Motivo")));
        assertThrows(AccessDeniedException.class, () -> service.otorgar(
                DOCENTE, "  ", pedido(Instant.now().plusSeconds(3600), "Motivo")));
    }

    // ---------- consultar / vigente ----------

    @Test
    void consultar_devuelveElVigente() {
        conAccesoVigente();

        Optional<AccesoExtraordinarioDto.Respuesta> r = service.consultar(DOCENTE);

        assertTrue(r.isPresent());
        assertEquals("Caso puntual", r.get().motivo());
    }

    @Test
    void consultar_sinAcceso_devuelveVacio() {
        assertTrue(service.consultar(DOCENTE).isEmpty());
    }

    @Test
    void consultar_docenteInexistente_lanza404() {
        when(docenteRepository.existsById(77L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.consultar(77L));
    }

    @Test
    void vigente_ignoraVencidosYLosQueAunNoEmpiezan() {
        AccesoExtraordinarioDocente vencido = acceso(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60));
        AccesoExtraordinarioDocente futuro = acceso(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
        when(accesoRepository.findByDocenteIdAndRevocadoEnIsNullOrderByFechaFinDesc(DOCENTE))
                .thenReturn(List.of(futuro, vencido));

        assertTrue(service.vigente(DOCENTE, Instant.now()).isEmpty());
    }

    // ---------- revocar ----------

    @Test
    void revocar_marcaRevocadoYRegistraBitacoraDesactivar() {
        conAccesoVigente();

        service.revocar(DOCENTE, ADMIN);

        ArgumentCaptor<AccesoExtraordinarioDocente> guardado = ArgumentCaptor.forClass(AccesoExtraordinarioDocente.class);
        verify(accesoRepository).save(guardado.capture());
        assertNotNull(guardado.getValue().getRevocadoEn());
        assertEquals(ADMIN, guardado.getValue().getRevocadoPor());

        ArgumentCaptor<Object> anterior = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> nuevo = ArgumentCaptor.forClass(Object.class);
        verify(bitacoraService).registrar(eq(AccionBitacora.DESACTIVAR), eq("ACCESO_EXTRAORDINARIO"),
                eq("9"), any(), anterior.capture(), nuevo.capture());
        assertFalse(((Map<?, ?>) anterior.getValue()).containsKey("revocado_en"));
        assertEquals(ADMIN, ((Map<?, ?>) nuevo.getValue()).get("revocado_por"));
    }

    @Test
    void revocar_sinAccesoVigente_lanza404() {
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.revocar(DOCENTE, ADMIN));
        assertEquals(AccesoExtraordinarioService.MSG_SIN_VIGENTE, ex.getMessage());
        verify(accesoRepository, never()).save(any());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    void revocar_unoYaVencido_cuentaComoSinVigente() {
        AccesoExtraordinarioDocente vencido = acceso(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60));
        when(accesoRepository.findByDocenteIdAndRevocadoEnIsNullOrderByFechaFinDesc(DOCENTE))
                .thenReturn(List.of(vencido));
        assertThrows(ResourceNotFoundException.class, () -> service.revocar(DOCENTE, ADMIN));
    }
}