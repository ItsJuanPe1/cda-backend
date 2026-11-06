package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.BitacoraDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.entity.Bitacora;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import co.edu.univalle.palmira.model.enums.ResultadoIngreso;
import co.edu.univalle.palmira.repository.BitacoraRepository;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** HU-058: servicio de bitácora. Datos de prueba ficticios. */
@ExtendWith(MockitoExtension.class)
class BitacoraServiceTest {

    private static final String ADMIN = "admin.prueba@correounivalle.edu.co";
    private static final String COORD = "programa.prueba@correounivalle.edu.co";

    @Mock
    private BitacoraRepository bitacoraRepository;

    @Mock
    private CoordinadorRepository coordinadorRepository;

    private BitacoraService service;

    @BeforeEach
    void setUp() {
        service = new BitacoraService(bitacoraRepository, coordinadorRepository,
                JsonMapper.builder().build(), "America/Bogota");
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    private static void sesion(String correo, int rol, String nombre, String apellido) {
        Jwt jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "HS256")
                .claim("email", correo)
                .claim("rol", rol)
                .claim("nombre", nombre)
                .claim("apellido", apellido)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Bitacora guardado() {
        ArgumentCaptor<Bitacora> captor = ArgumentCaptor.forClass(Bitacora.class);
        verify(bitacoraRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- Criterio 1

    @Test
    @DisplayName("Criterio 1: registra usuario, rol, acción, entidad, valor anterior y valor nuevo en JSON")
    void criterio1_registraCambioConUsuarioYValores() {
        sesion(ADMIN, AuthConstants.ROL_ADMIN, "Admin", "Prueba");

        service.registrar(AccionBitacora.EDITAR, "curso", "MAT101-50", 7L,
                Map.of("docenteId", 15), Map.of("docenteId", 22));

        Bitacora b = guardado();
        assertEquals(ADMIN, b.getUsuarioCorreo());
        assertEquals("ADMIN", b.getUsuarioRol());
        assertNull(b.getOperadorNombre());
        assertEquals(AccionBitacora.EDITAR, b.getAccion());
        assertEquals("CURSO", b.getEntidad());
        assertEquals("MAT101-50", b.getEntidadId());
        assertEquals(7L, b.getPeriodoId());
        assertEquals("{\"docenteId\":15}", b.getValorAnterior());
        assertEquals("{\"docenteId\":22}", b.getValorNuevo());
        assertNull(b.getResultado());
        assertNull(b.getMotivo());
    }

    @Test
    @DisplayName("Criterio 1: en la cuenta compartida de coordinación registra la persona que operó (coordinador del programa)")
    void criterio1_registraPersonaQueOperoCuentaCompartida() {
        sesion(COORD, AuthConstants.ROL_COORDINADOR, "Ana", "Prueba");
        Coordinador coordinador = Coordinador.builder()
                .correoInstitucional(COORD).nombreCompleto("Ana María Prueba Ficticia").build();
        when(coordinadorRepository.findByCorreoInstitucionalWithDetails(COORD)).thenReturn(Optional.of(coordinador));

        service.registrar(AccionBitacora.CREAR, "CURSO", "MAT101-51", null, null, Map.of("grupo", 51));

        Bitacora b = guardado();
        assertEquals(COORD, b.getUsuarioCorreo());
        assertEquals("COORDINADOR", b.getUsuarioRol());
        assertEquals("Ana María Prueba Ficticia", b.getOperadorNombre());
        assertNull(b.getValorAnterior());
    }

    @Test
    @DisplayName("Criterio 1: registrar exige la transacción del llamador; si falla, la operación se revierte")
    void criterio1_registrarParticipaEnLaTransaccionDelLlamador() throws NoSuchMethodException {
        Transactional tx = BitacoraService.class.getMethod("registrar", AccionBitacora.class, String.class,
                String.class, Long.class, Object.class, Object.class).getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());

        sesion(ADMIN, AuthConstants.ROL_ADMIN, "Admin", "Prueba");
        when(bitacoraRepository.save(any())).thenThrow(new IllegalStateException("fallo de base de datos"));
        assertThrows(IllegalStateException.class, () ->
                service.registrar(AccionBitacora.EDITAR, "CURSO", "MAT101-50", null, null, Map.of()));
    }

    @Test
    @DisplayName("Criterio 1: los ingresos no se registran por registrar(), sino por registrarIngreso()")
    void criterio1_registrarNoAceptaIngresos() {
        assertThrows(IllegalArgumentException.class, () ->
                service.registrar(AccionBitacora.INGRESO, "SESION", null, null, null, null));
        verifyNoInteractions(bitacoraRepository);
    }

    // ---------------------------------------------------------------- Criterio 2

    @Test
    @DisplayName("Criterio 2: registra un ingreso aceptado sin motivo")
    void criterio2_registraIngresoAceptado() {
        service.registrarIngreso(ADMIN, AuthConstants.ROL_ADMIN, ResultadoIngreso.ACEPTADO, null);

        Bitacora b = guardado();
        assertEquals(AccionBitacora.INGRESO, b.getAccion());
        assertEquals(BitacoraService.ENTIDAD_SESION, b.getEntidad());
        assertEquals(ADMIN, b.getUsuarioCorreo());
        assertEquals("ADMIN", b.getUsuarioRol());
        assertEquals(ResultadoIngreso.ACEPTADO, b.getResultado());
        assertNull(b.getMotivo());
        assertNull(b.getValorAnterior());
        assertNull(b.getValorNuevo());
    }

    @Test
    @DisplayName("Criterio 2: registra un ingreso rechazado con su motivo")
    void criterio2_registraIngresoRechazadoConMotivo() {
        service.registrarIngreso(null, null, ResultadoIngreso.RECHAZADO, MotivoRechazoIngreso.DOMINIO_NO_INSTITUCIONAL);

        Bitacora b = guardado();
        assertNull(b.getUsuarioCorreo());
        assertEquals(ResultadoIngreso.RECHAZADO, b.getResultado());
        assertEquals(MotivoRechazoIngreso.DOMINIO_NO_INSTITUCIONAL, b.getMotivo());
    }

    @Test
    @DisplayName("Criterio 2: registra el rechazo por fuera de fechas (RN-15) cuando se use ese motivo")
    void criterio2_registraRechazoPorFueraDeFechas() {
        service.registrarIngreso("docente.prueba@correounivalle.edu.co", AuthConstants.ROL_DOCENTE,
                ResultadoIngreso.RECHAZADO, MotivoRechazoIngreso.FUERA_DE_VENTANA);

        Bitacora b = guardado();
        assertEquals("DOCENTE", b.getUsuarioRol());
        assertEquals(MotivoRechazoIngreso.FUERA_DE_VENTANA, b.getMotivo());
    }

    @Test
    @DisplayName("Criterio 2: el ingreso usa una transacción propia para que el rechazo quede registrado")
    void criterio2_registrarIngresoUsaTransaccionPropia() throws NoSuchMethodException {
        Transactional tx = BitacoraService.class.getMethod("registrarIngreso", String.class, Integer.class,
                ResultadoIngreso.class, MotivoRechazoIngreso.class).getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation());
    }

    // ---------------------------------------------------------------- Criterio 3

    @Test
    @DisplayName("Criterio 3: filtra por usuario, entidad, periodo y rango de fechas (días en America/Bogota)")
    void criterio3_aplicaFiltros() {
        Bitacora registro = Bitacora.builder()
                .id(1042L).fecha(Instant.parse("2026-11-06T15:21:07Z")).usuarioCorreo(ADMIN).usuarioRol("ADMIN")
                .accion(AccionBitacora.EDITAR).entidad("CURSO").entidadId("MAT101-50").periodoId(7L)
                .valorAnterior("{\"docenteId\":15}").valorNuevo("{\"docenteId\":22}").build();
        when(bitacoraRepository.buscar(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(registro), PageRequest.of(0, 20), 1));

        BitacoraDto.Pagina pagina = service.consultar("  Admin.PRUEBA ", "curso", 7L,
                "2026-11-01", "2026-11-06", 0, 20);

        verify(bitacoraRepository).buscar(
                eq("admin.prueba"), eq("CURSO"), eq(7L),
                eq(Instant.parse("2026-11-01T05:00:00Z")),
                eq(Instant.parse("2026-11-07T05:00:00Z")),
                eq(PageRequest.of(0, 20)));
        assertEquals(1, pagina.totalElementos());
        assertEquals(1, pagina.totalPaginas());
        BitacoraDto.Response r = pagina.contenido().get(0);
        assertEquals("MAT101-50", r.entidadId());
        assertEquals(15, r.valorAnterior().get("docenteId").asInt());
        assertEquals(22, r.valorNuevo().get("docenteId").asInt());
    }

    @Test
    @DisplayName("Criterio 3: sin filtros consulta todo, paginado")
    void criterio3_sinFiltros() {
        when(bitacoraRepository.buscar(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        BitacoraDto.Pagina pagina = service.consultar(null, " ", null, null, "", 0, 20);

        verify(bitacoraRepository).buscar(isNull(), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 20)));
        assertTrue(pagina.contenido().isEmpty());
        assertEquals(0, pagina.totalElementos());
    }

    @Test
    @DisplayName("Criterio 3: la fecha inicial posterior a la final se rechaza")
    void criterio3_rangoInvertido() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.consultar(null, null, null, "2026-11-07", "2026-11-01", 0, 20));
        assertEquals(BitacoraService.ERROR_RANGO_FECHAS, ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 3: fecha mal formada o paginación fuera de rango se rechaza")
    void criterio3_parametrosInvalidos() {
        assertEquals(BitacoraService.ERROR_PARAMETROS, assertThrows(BadRequestException.class, () ->
                service.consultar(null, null, null, "06/11/2026", null, 0, 20)).getMessage());
        assertEquals(BitacoraService.ERROR_PARAMETROS, assertThrows(BadRequestException.class, () ->
                service.consultar(null, null, null, null, null, -1, 20)).getMessage());
        assertEquals(BitacoraService.ERROR_PARAMETROS, assertThrows(BadRequestException.class, () ->
                service.consultar(null, null, null, null, null, 0, 101)).getMessage());
        verifyNoInteractions(bitacoraRepository);
    }
}
