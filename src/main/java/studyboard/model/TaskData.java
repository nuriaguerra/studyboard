package studyboard.model;

import java.time.*; // LocalDate

/**
 * The data the user types into the task form.
 * A "record" is a compact immutable class: Java writes the constructor and the getters for us
 * The block { ... } below (the "compact constructor") runs every time a TaskData is created,
 * so an invalid TaskData can never exist.
 */
public record TaskData(String title, String notes, int subjectId, Priority priority, LocalDate deadline, double estimatedHours) {

    public TaskData { //compact constructor, it validates and cleans the values
        if (title == null || title.isBlank()) { //if it is null or empty (or only spaces)
            throw new IllegalArgumentException("The title cannot be empty.");
        }
        title = title.trim(); //here "title" is the parameter, we clean it by removing spaces at the start and the end
        if (title.length() > 80) {
            throw new IllegalArgumentException("The title is too long (maximum 80 characters).");
        }
        notes = (notes == null) ? "" : notes.trim(); //null notes become empty text
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
