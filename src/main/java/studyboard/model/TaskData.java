package studyboard.model;

import java.time.LocalDate;

/**
 * The data a user types into the task form. A "record" is a compact immutable class.
 * The compact constructor runs on creation, so an invalid TaskData can never exist:
 * the validation rules live in the Model, and the View only shows the message.
 */
public record TaskData(String title, String notes, int subjectId, Priority priority,
                       LocalDate deadline, double estimatedHours) {

    public TaskData {
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
        // isFinite rejects NaN and Infinity, which Double.parseDouble would accept.
        if (!Double.isFinite(estimatedHours) || estimatedHours < 0 || estimatedHours > 100) {
            throw new IllegalArgumentException("The estimate must be between 0 and 100 hours.");
        }
    }
}