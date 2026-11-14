package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.config.SecurityConfig;
import co.edu.univalle.palmira.dto.ElectivaProgramadaDto;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.GlobalExceptionHandler;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.service.ElectivaProgramadaService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ElectivaProgramadaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ElectivaProgramadaControllerTest.Mocks.class})
@TestPropertySource(properties = "auth.jwt.secret=0123456789012345678901234567890123456789")
class ElectivaProgramadaControllerTest {

    static final String CUERPO = "{\"codeProgram\":\"2135-DIU\",\"codeEspacio\":\"ELE-PROF-I\","
            + "\"codeCourse\":\"750022C\",\"cupos\":30}";

    @TestConfiguration
    static class Mocks {
        @Bean
        ElectivaProgramadaService electivaProgramadaService() {
            return mock(ElectivaProgramadaService.class);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired ElectivaProgramadaService servicio;

    @BeforeEach
    void limpiarMock() {
        reset(servicio);
    }

    private String token(String authority) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("u").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("email", "coord@correounivalle.edu.co")
                .claim("authorities", List.of(authority))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private ElectivaProgramadaDto.Response respuesta() {
        return new ElectivaProgramadaDto.Response(100L, "2135-DIU", "ELE-PROF-I", "Electiva Profesional I", 4,
                "750022C", "Inteligencia artificial", 50, "750022C-50", 30, Jornada.DIURNA, null, null,
                0, true, ElectivaProgramadaService.ALERTA_SIN_DOCENTES);
    }

    @Test
    void coordinador_crea_201() throws Exception {
        when(servicio.crear(any())).thenReturn(respuesta());
        mockMvc.perform(post("/api/programacion/electivas").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO).header("Authorization", token("ROLE_COORDINADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoCurso").value("750022C-50"))
                .andExpect(jsonPath("$.sinDocentesAsociados").value(true));
    }

    @Test
    void coordinador_consulta_200() throws Exception {
        when(servicio.listar("2135-DIU")).thenReturn(
                new ElectivaProgramadaDto.ListaResponse(7L, true, true, List.of(respuesta())));
        mockMvc.perform(get("/api/programacion/electivas").param("codeProgram", "2135-DIU")
                        .header("Authorization", token("ROLE_COORDINADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.electivas[0].codeEspacio").value("ELE-PROF-I"));
    }

    @Test
    void coordinador_modifica_200_yQuita_204() throws Exception {
        when(servicio.modificar(eq100(), any())).thenReturn(respuesta());
        mockMvc.perform(patch("/api/programacion/electivas/100").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cupos\":40}").header("Authorization", token("ROLE_COORDINADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/programacion/electivas/100")
                        .header("Authorization", token("ROLE_COORDINADOR")))
                .andExpect(status().isNoContent());
        verify(servicio).quitar(100L);
    }

    @Test
    void servicioLanza409_respondeConflict() throws Exception {
        when(servicio.crear(any())).thenThrow(new ConflictException(ElectivaProgramadaService.MSG_ESPACIO_OCUPADO));
        mockMvc.perform(post("/api/programacion/electivas").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO).header("Authorization", token("ROLE_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ElectivaProgramadaService.MSG_ESPACIO_OCUPADO));
    }

    @Test
    void docente_recibe403_enTodasLasRutas() throws Exception {
        String t = token("ROLE_DOCENTE");
        mockMvc.perform(post("/api/programacion/electivas").contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO).header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/programacion/electivas").param("codeProgram", "2135-DIU")
                .header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/programacion/electivas/100").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cupos\":40}").header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/programacion/electivas/100").header("Authorization", t))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinToken_recibe401() throws Exception {
        mockMvc.perform(get("/api/programacion/electivas").param("codeProgram", "2135-DIU"))
                .andExpect(status().isUnauthorized());
    }

    private static Long eq100() {
        return org.mockito.ArgumentMatchers.eq(100L);
    }
}