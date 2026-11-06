package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.config.SecurityConfig;
import co.edu.univalle.palmira.dto.BitacoraDto;
import co.edu.univalle.palmira.service.BitacoraService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-058: API de la bitácora con la configuración de seguridad real. No usa base de datos. */
@WebMvcTest(BitacoraController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "auth.jwt.secret=clave-de-prueba-de-al-menos-32-bytes-para-hs256")
class BitacoraControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BitacoraService bitacoraService;

    private static MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder req) {
        return req.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static MockHttpServletRequestBuilder comoCoordinador(MockHttpServletRequestBuilder req) {
        return req.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }

    @Test
    @DisplayName("Criterio 3: el administrador consulta la bitácora con filtros y recibe la página")
    void criterio3_adminConsulta() throws Exception {
        when(bitacoraService.consultar(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new BitacoraDto.Pagina(List.of(), 0, 20, 0, 0));

        mvc.perform(comoAdmin(get("/api/bitacora")
                        .param("usuario", "admin").param("entidad", "CURSO").param("periodoId", "7")
                        .param("desde", "2026-11-01").param("hasta", "2026-11-06")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    @DisplayName("Criterio 3: solo el administrador consulta; un coordinador recibe 403")
    void criterio3_noAdminRecibe403() throws Exception {
        mvc.perform(comoCoordinador(get("/api/bitacora"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bitacora")).andExpect(status().isUnauthorized());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    @DisplayName("Criterio 4: no existe forma de crear, modificar ni borrar registros desde la API (405)")
    void criterio4_soloLecturaDesdeLaApi() throws Exception {
        String cuerpo = "{\"accion\":\"EDITAR\"}";
        mvc.perform(comoAdmin(post("/api/bitacora").contentType("application/json").content(cuerpo)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(comoAdmin(put("/api/bitacora/1").contentType("application/json").content(cuerpo)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(comoAdmin(patch("/api/bitacora/1").contentType("application/json").content(cuerpo)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(comoAdmin(delete("/api/bitacora/1"))).andExpect(status().isMethodNotAllowed());
        mvc.perform(comoAdmin(delete("/api/bitacora"))).andExpect(status().isMethodNotAllowed());

        mvc.perform(comoCoordinador(delete("/api/bitacora/1"))).andExpect(status().isForbidden());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    @DisplayName("Criterio 3: un parámetro con tipo inválido responde 400")
    void criterio3_tipoInvalidoResponde400() throws Exception {
        mvc.perform(comoAdmin(get("/api/bitacora").param("pagina", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parámetros de consulta inválidos"));
    }
}
