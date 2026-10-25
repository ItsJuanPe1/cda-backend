package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.DocenteFoto;
import co.edu.univalle.palmira.repository.DocenteFotoRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    @Mock
    private DocenteRepository docenteRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private DocenteFotoRepository docenteFotoRepository;

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
    // Criterio 2: solo se editan foto, correo personal y teléfono
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

    // ---------------------------------------------------------------
    // Criterio 5: foto JPG o PNG de máximo 5 MB
    // ---------------------------------------------------------------

    @Test
    @DisplayName("HU-015 C5: una foto JPG válida se guarda como activa")
    void criterio5_fotoJpgValida() {
        perfilEncontrado();
        when(docenteFotoRepository.findByDocenteId(7L)).thenReturn(Optional.empty());

        DocenteDto.Response resp = docenteService.actualizarFoto(CORREO,
                new MockMultipartFile("foto", "foto.jpg", "image/jpeg", JPEG));

        ArgumentCaptor<DocenteFoto> captor = ArgumentCaptor.forClass(DocenteFoto.class);
        verify(docenteFotoRepository).save(captor.capture());
        assertEquals("image/jpeg", captor.getValue().getTipoMime());
        assertEquals(JPEG.length, captor.getValue().getTamanoBytes());
        assertTrue(captor.getValue().getActiva());
        assertTrue(resp.tieneFoto());
    }

    @Test
    @DisplayName("HU-015 C5: el tipo se decide por el contenido; un PNG con extensión .jpg se guarda como PNG")
    void criterio5_tipoPorContenido() {
        perfilEncontrado();
        when(docenteFotoRepository.findByDocenteId(7L)).thenReturn(Optional.empty());

        docenteService.actualizarFoto(CORREO, new MockMultipartFile("foto", "foto.jpg", "image/jpeg", PNG));

        ArgumentCaptor<DocenteFoto> captor = ArgumentCaptor.forClass(DocenteFoto.class);
        verify(docenteFotoRepository).save(captor.capture());
        assertEquals("image/png", captor.getValue().getTipoMime());
    }

    @Test
    @DisplayName("HU-015 C5: un archivo que no es JPG ni PNG muestra «Formato o tamaño no válido»")
    void criterio5_formatoInvalido() {
        perfilEncontrado();
        MockMultipartFile gif = new MockMultipartFile("foto", "foto.png", "image/png",
                new byte[]{'G', 'I', 'F', '8', '9', 'a'});

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> docenteService.actualizarFoto(CORREO, gif));
        assertEquals("Formato o tamaño no válido", ex.getMessage());
        verify(docenteFotoRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-015 C5: una foto de más de 5 MB muestra «Formato o tamaño no válido»")
    void criterio5_tamanoExcedido() {
        perfilEncontrado();
        byte[] grande = new byte[(int) DocenteService.FOTO_TAMANO_MAXIMO + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> docenteService.actualizarFoto(CORREO,
                        new MockMultipartFile("foto", "foto.jpg", "image/jpeg", grande)));
        assertEquals("Formato o tamaño no válido", ex.getMessage());
    }

    @Test
    @DisplayName("HU-015 C5: una foto de exactamente 5 MB es válida (borde)")
    void criterio5_tamanoLimiteExacto() {
        perfilEncontrado();
        when(docenteFotoRepository.findByDocenteId(7L)).thenReturn(Optional.empty());
        byte[] limite = new byte[(int) DocenteService.FOTO_TAMANO_MAXIMO];
        System.arraycopy(JPEG, 0, limite, 0, JPEG.length);

        assertDoesNotThrow(() -> docenteService.actualizarFoto(CORREO,
                new MockMultipartFile("foto", "foto.jpg", "image/jpeg", limite)));
    }

    @Test
    @DisplayName("HU-015 C5: un archivo vacío muestra «Formato o tamaño no válido»")
    void criterio5_archivoVacio() {
        perfilEncontrado();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> docenteService.actualizarFoto(CORREO,
                        new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[0])));
        assertEquals("Formato o tamaño no válido", ex.getMessage());
    }

    // ---------------------------------------------------------------
    // Criterio 6: reemplazar o eliminar la foto (una sola foto por docente)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("HU-015 C6: al reemplazar la foto se sobrescribe la única fila del docente y queda activa")
    void criterio6_reemplazaLaFotoExistente() {
        perfilEncontrado();
        DocenteFoto anterior = DocenteFoto.builder()
                .id(3L).docente(docente).contenido(JPEG).tipoMime("image/jpeg")
                .tamanoBytes(JPEG.length).activa(false).build();
        when(docenteFotoRepository.findByDocenteId(7L)).thenReturn(Optional.of(anterior));

        docenteService.actualizarFoto(CORREO, new MockMultipartFile("foto", "nueva.png", "image/png", PNG));

        ArgumentCaptor<DocenteFoto> captor = ArgumentCaptor.forClass(DocenteFoto.class);
        verify(docenteFotoRepository).save(captor.capture());
        assertEquals(3L, captor.getValue().getId(), "debe reutilizar la misma fila, no crear otra");
        assertArrayEquals(PNG, captor.getValue().getContenido());
        assertEquals("image/png", captor.getValue().getTipoMime());
        assertTrue(captor.getValue().getActiva());
    }

    @Test
    @DisplayName("HU-015 C6: al eliminar la foto se desactiva (RN-13), no se borra")
    void criterio6_eliminarDesactiva() {
        perfilEncontrado();
        DocenteFoto activa = DocenteFoto.builder()
                .id(3L).docente(docente).contenido(JPEG).tipoMime("image/jpeg")
                .tamanoBytes(JPEG.length).activa(true).build();
        when(docenteFotoRepository.findByDocenteIdAndActivaTrue(7L)).thenReturn(Optional.of(activa));

        docenteService.eliminarFoto(CORREO);

        assertFalse(activa.getActiva());
        verify(docenteFotoRepository).save(activa);
        verify(docenteFotoRepository, never()).delete(any());
        verify(docenteFotoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("HU-015 C6: tras eliminar la foto, el perfil indica que no tiene foto (imagen por defecto)")
    void criterio6_sinFotoTrasEliminar() {
        perfilEncontrado();
        when(docenteFotoRepository.existsByDocenteIdAndActivaTrue(7L)).thenReturn(false);

        assertFalse(docenteService.obtenerPorCorreo(CORREO).tieneFoto());
    }

    @Test
    @DisplayName("HU-015 C6: eliminar u obtener sin foto activa responde «No tiene foto registrada»")
    void criterio6_sinFotoActiva() {
        perfilEncontrado();
        when(docenteFotoRepository.findByDocenteIdAndActivaTrue(7L)).thenReturn(Optional.empty());

        assertEquals("No tiene foto registrada",
                assertThrows(ResourceNotFoundException.class, () -> docenteService.eliminarFoto(CORREO)).getMessage());
        assertEquals("No tiene foto registrada",
                assertThrows(ResourceNotFoundException.class, () -> docenteService.obtenerFoto(CORREO)).getMessage());
    }

    @Test
    @DisplayName("HU-015 C6: obtener la foto devuelve el contenido y el tipo de la foto activa")
    void criterio6_obtenerFoto() {
        perfilEncontrado();
        DocenteFoto activa = DocenteFoto.builder()
                .id(3L).docente(docente).contenido(PNG).tipoMime("image/png")
                .tamanoBytes(PNG.length).activa(true).build();
        when(docenteFotoRepository.findByDocenteIdAndActivaTrue(7L)).thenReturn(Optional.of(activa));

        DocenteDto.Foto foto = docenteService.obtenerFoto(CORREO);

        assertEquals("image/png", foto.tipoMime());
        assertArrayEquals(PNG, foto.contenido());
    }
}
