package studyboard.model;

import java.time.*; // LocalDate

/**
 * The data the user types into the task form.
 * A "record" is a compact immutable class: Java writes the constructor and the getters for us
 * (they are read as data.title(), without the word "get"). Immutable = it cannot be changed later.
 * The block { ... } below (the "compact constructor") runs every time a TaskData is created,
 * so an INVALID TaskData can never exist. The rules live in the Model;
 * the View only shows the error message to the user.
 */
public record TaskData(String title, String notes, int subjectId, Priority priority,
                       LocalDate deadline, double estimatedHours) {

    public TaskData { // compact constructor: it validates and cleans the values
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("The title cannot be empty.");
        }
        title = title.trim(); // here "title" is the parameter; Java copies it into the record field at the end
        if (title.length() > 80) {
            throw new IllegalArgumentException("The title is too long (maximum 80 characters).");
        }
        notes = (notes == null) ? "" : notes.trim(); // null notes become empty text
        if (priority == null) {
            throw new IllegalArgumentException("Please choose a priority.");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("The deadline is required.");
        }
        // isFinite rejects NaN and Infinity, which Double.parseDouble would accept without complaining
        if (!Double.isFinite(estimatedHours) || estimatedHours < 0 || estimatedHours > 100) {
            throw new IllegalArgumentException("The estimate must be between 0 and 100 hours.");
        }
    }
}
