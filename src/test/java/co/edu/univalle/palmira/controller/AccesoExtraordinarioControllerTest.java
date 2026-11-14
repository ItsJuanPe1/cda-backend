package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.config.SecurityConfig;
import co.edu.univalle.palmira.dto.AccesoExtraordinarioDto;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.GlobalExceptionHandler;
import co.edu.univalle.palmira.service.AccesoExtraordinarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccesoExtraordinarioController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AccesoExtraordinarioControllerTest.Mocks.class})
@TestPropertySource(properties = "auth.jwt.secret=0123456789012345678901234567890123456789")
class AccesoExtraordinarioControllerTest {

    static final String ADMIN = "admin@correounivalle.edu.co";
    static final String RUTA = "/api/admin/docentes/42/acceso-extraordinario";
    static final String CUERPO = "{\"fecha_fin\":\"2030-01-01T15:00:00Z\",\"motivo\":\"Cierre de notas\"}";

    @TestConfiguration
    static class Mocks {
        @Bean
        AccesoExtraordinarioService accesoExtraordinarioService() {
            return mock(AccesoExtraordinarioService.class);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired AccesoExtraordinarioService servicio;

    @BeforeEach
    void limpiarMock() {
        reset(servicio);
    }

    private String token(String authority) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("u").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("email", ADMIN)
                .claim("authorities", List.of(authority))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private AccesoExtraordinarioDto.Respuesta respuesta() {
        return new AccesoExtraordinarioDto.Respuesta(1L, 42L, Instant.parse("2029-12-31T15:00:00Z"),
                Instant.parse("2030-01-01T15:00:00Z"), "Cierre de notas", ADMIN);
    }

    @Test
    void admin_otorga_201ConElCuerpoYElCorreoDelToken() throws Exception {
        when(servicio.otorgar(eq(42L), eq(ADMIN), any())).thenReturn(respuesta());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.docente_id").value(42))
                .andExpect(jsonPath("$.motivo").value("Cierre de notas"));

        verify(servicio).otorgar(eq(42L), eq(ADMIN), any());
    }

    @Test
    void admin_otorgaConMotivoVacio_400() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha_fin\":\"2030-01-01T15:00:00Z\",\"motivo\":\"\"}")
                        .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_otorgaCuandoYaHayUnoVigente_409() throws Exception {
        when(servicio.otorgar(eq(42L), eq(ADMIN), any()))
                .thenThrow(new ConflictException(AccesoExtraordinarioService.MSG_YA_VIGENTE));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AccesoExtraordinarioService.MSG_YA_VIGENTE));
    }

    @Test
    void admin_consultaVigente_200() throws Exception {
        when(servicio.consultar(42L)).thenReturn(Optional.of(respuesta()));

        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha_fin").exists());
    }

    @Test
    void admin_consultaSinAcceso_204() throws Exception {
        when(servicio.consultar(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_revoca_204() throws Exception {
        mockMvc.perform(delete(RUTA).header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isNoContent());

        verify(servicio).revocar(42L, ADMIN);
    }

    @Test
    void coordinador_recibe403_enLasTresRutas() throws Exception {
        String t = "Bearer " + token("ROLE_COORDINADOR");
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                .header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA).header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(delete(RUTA).header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void docente_recibe403_enLasTresRutas() throws Exception {
        String t = "Bearer " + token("ROLE_DOCENTE");
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                .header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA).header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(delete(RUTA).header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void sinToken_recibe401() throws Exception {
        mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }
}