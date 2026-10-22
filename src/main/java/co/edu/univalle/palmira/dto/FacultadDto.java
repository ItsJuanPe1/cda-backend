package co.edu.univalle.palmira.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FacultadDto {

    public record Response(
            Integer id,
            String nombre,
            String descripcion
    ) {}

    public record CreateRequest(
            @NotBlank(message = "El nombre de la facultad no puede estar vacío")
            @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
            String nombre,

            String descripcion
    ) {}
}
