package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.FacultadDto;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Facultad;
import co.edu.univalle.palmira.repository.FacultadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacultadService {

    private final FacultadRepository facultadRepository;

    @Transactional(readOnly = true)
    public List<FacultadDto.Response> getAll() {
        return facultadRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FacultadDto.Response getById(Integer id) {
        Facultad facultad = facultadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facultad no encontrada con id: " + id));
        return toResponse(facultad);
    }

    @Transactional
    public FacultadDto.Response create(FacultadDto.CreateRequest request) {
        Facultad facultad = Facultad.builder()
                .nombre(request.nombre().trim())
                .descripcion(request.descripcion() != null ? request.descripcion().trim() : null)
                .build();
        return toResponse(facultadRepository.save(facultad));
    }

    private FacultadDto.Response toResponse(Facultad f) {
        return new FacultadDto.Response(f.getId(), f.getNombre(), f.getDescripcion());
    }
}
