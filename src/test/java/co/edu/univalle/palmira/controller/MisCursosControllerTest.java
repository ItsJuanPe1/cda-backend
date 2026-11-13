package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.config.SecurityConfig;
import co.edu.univalle.palmira.dto.MisCursosDto;
import co.edu.univalle.palmira.exception.GlobalExceptionHandler;
import co.edu.univalle.palmira.service.MisCursosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MisCursosController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MisCursosControllerTest.Mocks.class})
@TestPropertySource(properties = "auth.jwt.secret=0123456789012345678901234567890123456789")
class MisCursosControllerTest {

    static final String CORREO = "ana@correounivalle.edu.co";

    @TestConfiguration
    static class Mocks {
        @Bean
        MisCursosService misCursosService() {
            return mock(MisCursosService.class);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired MisCursosService misCursosService;
    @BeforeEach
    void limpiarMock() {
        reset(misCursosService);
    }

    private String token(String authority) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("u")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("email", CORREO)
                .claim("authorities", List.of(authority))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private void respuestaDe10Horas() {
        when(misCursosService.consultar(anyString())).thenReturn(new MisCursosDto.Respuesta(
                new MisCursosDto.Periodo(9L, "2026-2"), 10, 20, 10,
                List.of(new MisCursosDto.Curso(1L, "710022C-50", "Cálculo", "Ingeniería", "DIURNA", 10, false))));
    }

    @Test
    void docente_obtiene200ConSusCursos() throws Exception {
        respuestaDe10Horas();

        mockMvc.perform(get("/api/carga/mis-cursos")
                        .header("Authorization", "Bearer " + token("DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_horas").value(10))
                .andExpect(jsonPath("$.limite").value(20))
                .andExpect(jsonPath("$.periodo.nombre").value("2026-2"))
                .andExpect(jsonPath("$.cursos[0].codigo_grupo").value("710022C-50"));

        verify(misCursosService).consultar(CORREO);
    }

    @Test
    void docente_conPrefijoRole_tambienPasa() throws Exception {
        respuestaDe10Horas();

        mockMvc.perform(get("/api/carga/mis-cursos")
                        .header("Authorization", "Bearer " + token("ROLE_DOCENTE")))
                .andExpect(status().isOk());
    }

    @Test
    void coordinador_recibe403() throws Exception {
        mockMvc.perform(get("/api/carga/mis-cursos")
                        .header("Authorization", "Bearer " + token("ROLE_COORDINADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_recibe403() throws Exception {
        mockMvc.perform(get("/api/carga/mis-cursos")
                        .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinToken_recibe401() throws Exception {
        mockMvc.perform(get("/api/carga/mis-cursos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void docente_noPuedeUsarLasRutasDeCargaDeOtrosDocentes() throws Exception {
        mockMvc.perform(get("/api/carga/docentes/1").param("periodoId", "9")
                        .header("Authorization", "Bearer " + token("DOCENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void servicioLanzaAccessDenied_responde403() throws Exception {
        when(misCursosService.consultar(anyString()))
                .thenThrow(new AccessDeniedException("No tiene permisos para esta acción"));

        mockMvc.perform(get("/api/carga/mis-cursos")
                        .header("Authorization", "Bearer " + token("DOCENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tiene permisos para esta acción"));
    }
}