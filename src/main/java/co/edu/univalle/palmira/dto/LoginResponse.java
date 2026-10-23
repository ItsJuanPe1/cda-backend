package co.edu.univalle.palmira.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        Integer id,
        String nombre,
        String apellido,
        String email,
        Integer rol,
        String mensaje
) {
}
