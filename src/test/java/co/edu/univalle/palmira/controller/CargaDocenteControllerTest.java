package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.config.SecurityConfig;
import co.edu.univalle.palmira.dto.CargaDocenteDto;
import co.edu.univalle.palmira.service.CargaDocenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CargaDocenteController.class)
@Import({SecurityConfig.class, CargaDocenteControllerTest.Mocks.class})
@TestPropertySource(properties = "auth.jwt.secret=0123456789012345678901234567890123456789")
class CargaDocenteControllerTest {

    @TestConfiguration
    static class Mocks {
        @Bean
        CargaDocenteService cargaDocenteService() {
            return mock(CargaDocenteService.class);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired CargaDocenteService cargaDocenteService;

    private String token(String... authorities) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("u")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("authorities", List.of(authorities))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private void detalleDe15Horas() {
        when(cargaDocenteService.consultar(anyLong(), anyLong())).thenReturn(
                new CargaDocenteDto.Detalle(12L, 5L, 15, 20, 5, List.of(), 0));
    }

    @Test
    void admin_consultaDetalle_200() throws Exception {
        detalleDe15Horas();

        mockMvc.perform(get("/api/carga/docentes/12").param("periodoId", "5")
                        .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_horas").value(15))
                .andExpect(jsonPath("$.disponibles").value(5))
                .andExpect(jsonPath("$.otros_programas_horas").value(0));
    }

    @Test
    void coordinador_consultaDetalle_200() throws Exception {
        detalleDe15Horas();

        mockMvc.perform(get("/api/carga/docentes/12").param("periodoId", "5")
                        .header("Authorization", "Bearer " + token("ROLE_COORDINADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void coordinador_listaPorPeriodo_200() throws Exception {
        when(cargaDocenteService.listarPorPeriodo(5L)).thenReturn(
                List.of(new CargaDocenteDto.Resumen(12L, 15, 20, 5, 0)));

        mockMvc.perform(get("/api/carga/docentes").param("periodoId", "5")
                        .header("Authorization", "Bearer " + token("ROLE_COORDINADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docente_id").value(12))
                .andExpect(jsonPath("$[0].total_horas").value(15));
    }

    @Test
    void docente_recibe403() throws Exception {
        mockMvc.perform(get("/api/carga/docentes/12").param("periodoId", "5")
                        .header("Authorization", "Bearer " + token("ROLE_DOCENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinToken_recibe401() throws Exception {
        mockMvc.perform(get("/api/carga/docentes/12").param("periodoId", "5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinPeriodoId_recibe400() throws Exception {
        mockMvc.perform(get("/api/carga/docentes/12")
                        .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_comoCoordinador_recibe403() throws Exception {
        mockMvc.perform(post("/api/carga/docentes")
                        .header("Authorization", "Bearer " + token("ROLE_COORDINADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_comoAdmin_recibe405PorqueNoHayEscritura() throws Exception {
        mockMvc.perform(post("/api/carga/docentes")
                        .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isMethodNotAllowed());
    }
}