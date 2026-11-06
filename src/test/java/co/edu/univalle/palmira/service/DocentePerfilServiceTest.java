package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Facultad;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.FacultadRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HU-015: editar los datos de mi perfil (docente). Datos de prueba ficticios.
 */
@ExtendWith(MockitoExtension.class)
class DocentePerfilServiceTest {

    private static final String CORREO = "docente.prueba@correounivalle.edu.co";

    @Mock
    private DocenteRepository docenteRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private FacultadRepository facultadRepository;

    @InjectMocks
    private DocenteService docenteService;

    private Docente docente;

    @BeforeEach
    void setUp() {
        docente = Docente.builder()
                .id(7L)
                .codigo("DOC-0007")
                .nombres("Ana")
                .apellidos("Prueba")
                .correoInstitucional(CORREO)
                .correoPersonal("ana@example.com")
                .telefono("3001234567")
                .build();
    }

    private void perfilEncontrado() {
        when(docenteRepository.findByCorreoInstitucionalAndEstadoTrue(CORREO)).thenReturn(Optional.of(docente));
    }

    // ---------------------------------------------------------------
    // Criterio 2: solo se editan contacto y facultad
    // ---------------------------------------------------------------

    @Test
    @DisplayName("HU-015 C2: al guardar el contacto se actualizan correo personal y teléfono, y nada más")
    void criterio2_actualizaSoloContacto() {
        perfilEncontrado();
        when(docenteRepository.save(any(Docente.class))).thenAnswer(inv -> inv.getArgument(0));

        DocenteDto.Response resp = docenteService.actualizarContacto(CORREO,
                new DocenteDto.ContactoRequest("  Nuevo.Correo@Example.com ", "3109876543"));

        assertEquals("nuevo.correo@example.com", resp.correoPersonal());
        assertEquals("3109876543", resp.telefono());
        assertEquals("Ana", resp.nombres());
        assertEquals("Prueba", resp.apellidos());
        assertEquals(CORREO, resp.correoInstitucional());
    }

    @Test
    @DisplayName("HU-015 C2: correo personal y teléfono vacíos dejan los campos sin valor (son opcionales)")
    void criterio2_contactoVacioQuedaNulo() {
        perfilEncontrado();
        when(docenteRepository.save(any(Docente.class))).thenAnswer(inv -> inv.getArgument(0));

        DocenteDto.Response resp = docenteService.actualizarContacto(CORREO,
                new DocenteDto.ContactoRequest("", "  "));

        assertNull(resp.correoPersonal());
        assertNull(resp.telefono());
    }

    @Test
    @DisplayName("HU-015: el docente puede actualizar su facultad de adscripción")
    void criterio2_actualizaFacultad() {
        perfilEncontrado();
        Facultad facultadIngenieria = Facultad.builder().id(4).nombre("Facultad de Ingeniería").build();
        when(facultadRepository.findById(4)).thenReturn(Optional.of(facultadIngenieria));
        when(docenteRepository.save(any(Docente.class))).thenAnswer(inv -> inv.getArgument(0));

        DocenteDto.Response resp = docenteService.actualizarContacto(CORREO,
                new DocenteDto.ContactoRequest("ana@example.com", "3001234567", 4));

        assertEquals(4, resp.idFacultad());
        assertEquals("Facultad de Ingeniería", resp.nombreFacultad());
    }

    @Test
    @DisplayName("HU-022 C6: el perfil se busca por el correo de la sesión; si no hay perfil activo responde 404")
    void perfilPropio_noEncontrado() {
        when(docenteRepository.findByCorreoInstitucionalAndEstadoTrue(CORREO)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> docenteService.obtenerPorCorreo(CORREO));
        assertEquals("No se encontró su perfil de docente", ex.getMessage());
    }

    // ---------------------------------------------------------------
    // Criterio 7: sin cambios
    // ---------------------------------------------------------------

    @Test
    @DisplayName("HU-015 C7: si no modifiqué nada, muestra «No hay cambios para guardar»")
    void criterio7_sinCambios() {
        perfilEncontrado();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> docenteService.actualizarContacto(CORREO,
                        new DocenteDto.ContactoRequest("ANA@example.com ", "3001234567")));

        assertEquals("No hay cambios para guardar", ex.getMessage());
        verify(docenteRepository, never()).save(any());
    }
}
