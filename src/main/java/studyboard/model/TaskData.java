package studyboard.model;

import java.time.*; // LocalDate

/**
 * Los datos que el usuario escribe en el formulario de tarea.
 * "record" = clase compacta e inmutable: Java crea solo el constructor y los getters
 * (se leen como data.title(), sin "get").
 * El bloque { ... } de abajo (constructor compacto) se ejecuta al crear el objeto:
 * así NUNCA existe un TaskData inválido. Las reglas viven en el Model;
 * la View solo enseña el mensaje de error.
 */
public record TaskData(String title, String notes, int subjectId, Priority priority,
                       LocalDate deadline, double estimatedHours) {

    public TaskData { // constructor compacto: valida y limpia
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("The title cannot be empty.");
        }
        title = title.trim();
        if (title.length() > 80) {
            throw new IllegalArgumentException("The title is too long (maximum 80 characters).");
        }
        notes = (notes == null) ? "" : notes.trim();
        if (priority == null) {
            throw new IllegalArgumentException("Please choose a priority.");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("The deadline is required.");
        }
        // isFinite rechaza NaN e Infinity, que parseDouble sí aceptaría
        if (!Double.isFinite(estimatedHours) || estimatedHours < 0 || estimatedHours > 100) {
            throw new IllegalArgumentException("The estimate must be between 0 and 100 hours.");
        }
    }
}
