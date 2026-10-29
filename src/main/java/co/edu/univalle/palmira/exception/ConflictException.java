package co.edu.univalle.palmira.exception;

public class ConflictException extends RuntimeException {

    private final Long existingId;

    public ConflictException(String message, Long existingId) {
        super(message);
        this.existingId = existingId;
    }

    public Long getExistingId() {
        return existingId;
    }
}
