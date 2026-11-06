package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteAsignaturaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.OrigenAsociacion;
import co.edu.univalle.palmira.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HU-049: histórico de asignaturas dictadas por docente. Datos ficticios.
 */
@ExtendWith(MockitoExtension.class)
class DocenteAsignaturaServiceTest {

    @Mock private DocenteAsignaturaRepository asociacionRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private AsignaturaRepository asignaturaRepository;
    @Mock private ProgramaRepository programaRepository;
    @Mock private PensumAcademicoRepository pensumRepository;
    @Mock private FacultadRepository facultadRepository;

    @InjectMocks private DocenteAsignaturaService service;

    private Facultad facultad;
    private Docente docente;
    private Asignatura asignatura;
    private Programa programa;

    @BeforeEach
    void setUp() {
        facultad = Facultad.builder()
                .id(1)
                .nombre("Facultad de Ingeniería")
                .build();
        programa = Programa.builder()
                .codeProgram("2711")
                .nameProgram("Tecnología en Desarrollo de Software")
                .estado(EstadoPrograma.ACTIVO)
                .idFacultadPrograma(1)
                .build();
        docente = Docente.builder()
                .id(7L)
                .codigo("D007")
                .nombres("Ana")
                .apellidos("Pérez")
                .correoInstitucional("ana.perez@correo.edu.co")
                .numeroDocumento("123456789")
                .estado(true)
                .facultad(facultad)
                .programa(programa)
                .build();
        asignatura = Asignatura.builder()
                .codeCourse("MAT101")
                .nameCourse("Cálculo I")
                .activo(true)
                .idFacultad(1)
                .build();
    }

    private void guardarDevuelveLoMismo() {
        when(asociacionRepository.save(any(DocenteAsignatura.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Criterio 1: asocia un docente a una asignatura de su facultad")
    void criterio1_asociaDocenteAsignatura() {
        when(docenteRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(docente));
        when(asignaturaRepository.findById("MAT101")).thenReturn(Optional.of(asignatura));
        when(asociacionRepository.findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(7L, "MAT101", 1))
                .thenReturn(Optional.empty());
        guardarDevuelveLoMismo();

        DocenteAsignaturaDto.Response response = service.asociar(
                new DocenteAsignaturaDto.CreateRequest(7L, " mat101 "));

        assertEquals(7L, response.docenteId());
        assertEquals("MAT101", response.codeCourse());
        assertEquals(1, response.idFacultad());
        assertEquals("Facultad de Ingeniería", response.nombreFacultad());
        assertEquals(OrigenAsociacion.MANUAL, response.origen());
        assertTrue(response.activo());

        ArgumentCaptor<DocenteAsignatura> captor = ArgumentCaptor.forClass(DocenteAsignatura.class);
        verify(asociacionRepository).save(captor.capture());
        assertEquals(OrigenAsociacion.MANUAL, captor.getValue().getOrigen());
        assertEquals(facultad, captor.getValue().getFacultad());
    }

    @Test
    @DisplayName("Criterio 1: quitar desactiva la asociación y no la elimina (RN-13)")
    void criterio1_quitarDesactivaSinBorrar() {
        DocenteAsignatura asociacion = DocenteAsignatura.builder()
                .id(99L).docente(docente).asignatura(asignatura).facultad(facultad)
                .activo(true).origen(OrigenAsociacion.MANUAL).build();
        when(asociacionRepository.findById(99L)).thenReturn(Optional.of(asociacion));
        guardarDevuelveLoMismo();

        DocenteAsignaturaDto.Response response = service.quitar(99L);

        assertFalse(response.activo());
        assertNotNull(response.desactivadoAt());
        verify(asociacionRepository, never()).delete(any());
        verify(asociacionRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Criterio 3: acepta la asignatura si está en la malla de un programa de la facultad")
    void criterio3_aceptaAsignaturaDeLaFacultad() {
        asignatura.setIdFacultad(2);
        when(docenteRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(docente));
        when(asignaturaRepository.findById("MAT101")).thenReturn(Optional.of(asignatura));
        when(programaRepository.findCodeProgramByIdFacultadPrograma(1)).thenReturn(List.of("2711", "2722"));
        when(pensumRepository.existsByIdCodeProgramInAndIdCodeCourse(anyCollection(), eq("MAT101")))
                .thenReturn(true);
        when(asociacionRepository.findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(7L, "MAT101", 1))
                .thenReturn(Optional.empty());
        guardarDevuelveLoMismo();

        DocenteAsignaturaDto.Response response = service.asociar(
                new DocenteAsignaturaDto.CreateRequest(7L, "MAT101"));

        assertEquals("MAT101", response.codeCourse());
        assertEquals(1, response.idFacultad());
    }

    @Test
    @DisplayName("Criterio 3: rechaza una asignatura fuera de la facultad del docente")
    void criterio3_rechazaAsignaturaFueraDeFacultad() {
        asignatura.setIdFacultad(2);
        when(docenteRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(docente));
        when(asignaturaRepository.findById("MAT101")).thenReturn(Optional.of(asignatura));
        when(programaRepository.findCodeProgramByIdFacultadPrograma(1)).thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.asociar(
                new DocenteAsignaturaDto.CreateRequest(7L, "MAT101")));

        assertEquals(DocenteAsignaturaService.MSG_ASIGNATURA_FUERA_DE_FACULTAD, ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 3: rechaza al docente sin facultad ni programa")
    void criterio3_rechazaDocenteSinFacultad() {
        docente.setFacultad(null);
        docente.setPrograma(null);
        when(docenteRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(docente));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.asociar(
                new DocenteAsignaturaDto.CreateRequest(7L, "MAT101")));

        assertEquals(DocenteAsignaturaService.MSG_SIN_FACULTAD, ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 3: deriva la facultad desde el programa del docente")
    void criterio3_derivaFacultadDelPrograma() {
        docente.setFacultad(null);
        when(docenteRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(docente));
        when(facultadRepository.findById(1)).thenReturn(Optional.of(facultad));
        when(asignaturaRepository.findById("MAT101")).thenReturn(Optional.of(asignatura));
        when(asociacionRepository.findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(7L, "MAT101", 1))
                .thenReturn(Optional.empty());
        guardarDevuelveLoMismo();

        DocenteAsignaturaDto.Response response = service.asociar(
                new DocenteAsignaturaDto.CreateRequest(7L, "MAT101"));

        assertEquals(1, response.idFacultad());
        assertEquals("Facultad de Ingeniería", response.nombreFacultad());
    }

    @Test
    @DisplayName("Criterio 3: rechaza duplicados activos en la misma facultad")
    void criterio3_rechazaDuplicadoActivo() {
        when(docenteRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(docente));
        when(asignaturaRepository.findById("MAT101")).thenReturn(Optional.of(asignatura));
        DocenteAsignatura existente = DocenteAsignatura.builder()
                .id(5L).docente(docente).asignatura(asignatura).facultad(facultad).activo(true).build();
        when(asociacionRepository.findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(7L, "MAT101", 1))
                .thenReturn(Optional.of(existente));

        assertThrows(ConflictException.class, () -> service.asociar(
                new DocenteAsignaturaDto.CreateRequest(7L, "MAT101")));
    }

    @Test
    @DisplayName("Criterio 4: registrar desde programación crea el histórico con origen PROGRAMACION")
    void criterio4_registraDesdeCursos() {
        PeriodoAcademico periodo = PeriodoAcademico.builder().id(3L).nombre("2026-1").build();
        CursoProgramacion curso = CursoProgramacion.builder()
                .periodo(periodo).programa(programa).asignatura(asignatura).docente(docente).build();
        when(facultadRepository.findById(1)).thenReturn(Optional.of(facultad));
        when(asociacionRepository.findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(7L, "MAT101", 1))
                .thenReturn(Optional.empty());
        guardarDevuelveLoMismo();

        service.registrarDesdeCursos(List.of(curso));

        ArgumentCaptor<DocenteAsignatura> captor = ArgumentCaptor.forClass(DocenteAsignatura.class);
        verify(asociacionRepository).save(captor.capture());
        DocenteAsignatura guardada = captor.getValue();
        assertEquals(OrigenAsociacion.PROGRAMACION, guardada.getOrigen());
        assertEquals(facultad, guardada.getFacultad());
        assertTrue(guardada.getActivo());
        assertEquals(3L, guardada.getPeriodo().getId());
    }

    @Test
    @DisplayName("Criterio 4: registrar desde programación reactiva una asociación inactiva")
    void criterio4_reactivaAsociacionExistente() {
        PeriodoAcademico periodo = PeriodoAcademico.builder().id(3L).nombre("2026-1").build();
        CursoProgramacion curso = CursoProgramacion.builder()
                .periodo(periodo).programa(programa).asignatura(asignatura).docente(docente).build();
        DocenteAsignatura existente = DocenteAsignatura.builder()
                .id(8L).docente(docente).asignatura(asignatura).facultad(facultad)
                .activo(false).desactivadoAt(java.time.Instant.now()).build();
        when(facultadRepository.findById(1)).thenReturn(Optional.of(facultad));
        when(asociacionRepository.findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(7L, "MAT101", 1))
                .thenReturn(Optional.of(existente));
        guardarDevuelveLoMismo();

        service.registrarDesdeCursos(List.of(curso));

        assertTrue(existente.getActivo());
        assertNull(existente.getDesactivadoAt());
        assertEquals(3L, existente.getPeriodo().getId());
    }

    @Test
    @DisplayName("Criterio 5: consulta normalizando el código y filtrando por facultad")
    void criterio5_listarNormalizaFiltros() {
        when(asociacionRepository.buscar(7L, "MAT101", 1, true)).thenReturn(List.of());

        service.listar(7L, " mat101 ", 1, true);

        verify(asociacionRepository).buscar(7L, "MAT101", 1, true);
    }
}
