package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioAccesoServiceTest {

    private UsuarioRepository repo;
    private UsuarioAccesoService service;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        service = new UsuarioAccesoService(repo);
    }

    @Test
    void crea_elUsuarioDelDocenteNuevo_conSuCelular() {
        when(repo.buscarPorCorreoInstitucional("ana.perez@correounivalle.edu.co")).thenReturn(Optional.empty());

        service.sincronizar(null, "Ana", "Pérez", "Ana.Perez@correounivalle.edu.co", "ana@gmail.com", "3001234567",
                AuthConstants.ROL_DOCENTE, true);

        ArgumentCaptor<Usuario> u = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).save(u.capture());
        assertEquals("ana.perez@correounivalle.edu.co", u.getValue().getEmailInstitucional());
        assertEquals("ana@gmail.com", u.getValue().getEmailPersonal());
        assertEquals("3001234567", u.getValue().getCelular());
        assertEquals(3, u.getValue().getRol());
    }

    @Test
    void sinCorreoPersonal_laColumnaQuedaVacia() {
        when(repo.buscarPorCorreoInstitucional(any())).thenReturn(Optional.empty());
        service.sincronizar(null, "Juan Carlos Pérez López", "jc@correounivalle.edu.co", null, null,
                AuthConstants.ROL_COORDINADOR, true);

        ArgumentCaptor<Usuario> u = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).save(u.capture());
        assertNull(u.getValue().getEmailPersonal());
        assertNull(u.getValue().getCelular());
        assertEquals(2, u.getValue().getRol());
    }

    @Test
    void divideElNombre_dosNombresDosApellidos() {
        assertArrayEquals(new String[]{"Juan Carlos", "Pérez Gómez"},
                UsuarioAccesoService.dividirNombre("Juan Carlos Pérez Gómez"));
    }

    @Test
    void divideElNombre_unNombreDosApellidos() {
        assertArrayEquals(new String[]{"María", "Pérez Gómez"},
                UsuarioAccesoService.dividirNombre("  María   Pérez Gómez "));
    }

    @Test
    void divideElNombre_casosCortos() {
        assertArrayEquals(new String[]{"Ana", "Ruiz"}, UsuarioAccesoService.dividirNombre("Ana Ruiz"));
        assertArrayEquals(new String[]{"Ana", ""}, UsuarioAccesoService.dividirNombre("Ana"));
        assertArrayEquals(new String[]{"", ""}, UsuarioAccesoService.dividirNombre(null));
    }

    @Test
    void siYaExiste_laActualiza_yNoCreaOtra_sinCambiarElRol() {
        Usuario existente = Usuario.builder().id(7).nombre("Ana").apellido("Pérez").rol(AuthConstants.ROL_DOCENTE)
                .emailInstitucional("ana@correounivalle.edu.co").emailPersonal("a@x.com").build();
        when(repo.buscarPorCorreoInstitucional("ana@correounivalle.edu.co")).thenReturn(Optional.of(existente));

        service.sincronizar(null, "Ana María", "Pérez Gómez", "ana@correounivalle.edu.co", "nuevo@x.com",
                "3011234567", AuthConstants.ROL_DOCENTE, true);

        assertEquals("Ana María", existente.getNombre());
        assertEquals("nuevo@x.com", existente.getEmailPersonal());
        assertEquals("3011234567", existente.getCelular());
        assertEquals(AuthConstants.ROL_DOCENTE, existente.getRol());
        verify(repo).save(existente);
    }

    @Test
    void siCambiaElCorreoInstitucional_siguePorElCorreoAnterior() {
        Usuario existente = Usuario.builder().id(7).nombre("Ana").apellido("Pérez").rol(AuthConstants.ROL_DOCENTE)
                .emailInstitucional("viejo@correounivalle.edu.co").build();
        when(repo.buscarPorCorreoInstitucional("viejo@correounivalle.edu.co")).thenReturn(Optional.of(existente));

        service.sincronizar("viejo@correounivalle.edu.co", "Ana", "Pérez", "nuevo@correounivalle.edu.co", null, null,
                AuthConstants.ROL_DOCENTE, true);

        assertEquals("nuevo@correounivalle.edu.co", existente.getEmailInstitucional());
        verify(repo, times(1)).save(any(Usuario.class));
    }

    @Test
    void quitarElCorreoPersonal_loBorraDeUsuarios() {
        Usuario existente = Usuario.builder().id(7).nombre("Ana").apellido("Pérez").rol(AuthConstants.ROL_DOCENTE)
                .emailInstitucional("ana@correounivalle.edu.co").emailPersonal("a@x.com").build();
        when(repo.buscarPorCorreoInstitucional("ana@correounivalle.edu.co")).thenReturn(Optional.of(existente));
        service.sincronizar(null, "Ana", "Pérez", "ana@correounivalle.edu.co", "", null,
                AuthConstants.ROL_DOCENTE, true);
        assertNull(existente.getEmailPersonal());
    }

    @Test
    void cambiarEstado_noModificaTablaUsuarios() {
        service.cambiarEstado("C@correounivalle.edu.co", false);
        service.cambiarEstado("c@correounivalle.edu.co", true);
        verify(repo, never()).save(any());
    }

    @Test
    void cambiarEstado_sinFila_noHaceNada() {
        when(repo.buscarPorCorreoInstitucional(any())).thenReturn(Optional.empty());
        service.cambiarEstado("x@correounivalle.edu.co", false);
        verify(repo, never()).save(any());
    }
}
