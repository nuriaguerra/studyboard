package studyboard.model;

import java.util.*; // Comparator

/**
 * The ways the board can be sorted.
 * A Comparator is "a rule that decides which of two tasks goes first".
 * thenComparing = tie-breaker: if two tasks are equal for the first rule, look at the next one.
 * The last tie-breaker is always the id, so the order is stable (it never changes randomly).
 *
 * Task::getDeadline is a "method reference", a short way to write t -> t.getDeadline().
 * Comparator.<Priority>reverseOrder() = the natural order of the enum (LOW, MEDIUM, HIGH)
 * but backwards, so HIGH goes first. The <Priority> tells Java which type we mean.
 */
public enum SortOrder {
    // By deadline (earliest first); if equal, higher priority first; if still equal, by id
    DEADLINE("Deadline",
            Comparator.comparing(Task::getDeadline)                                  // rule 1: earliest date first
                    .thenComparing(Task::getPriority, Comparator.<Priority>reverseOrder()) // rule 2: HIGH before LOW
                    .thenComparingInt(Task::getId)),                                 // rule 3: lower id first
    // By priority (high first); if equal, closest deadline; if still equal, by id
    PRIORITY("Priority",
            Comparator.comparing(Task::getPriority, Comparator.<Priority>reverseOrder())
                    .thenComparing(Task::getDeadline)
                    .thenComparingInt(Task::getId)),
    // Alphabetical, ignoring upper/lower case; if equal, by id
    TITLE("Title",
            Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Task::getId));

    private final String label;                // text shown to the user in the "Sort by" list
    private final Comparator<Task> comparator; // the rule itself; Controller passes it to list.sort(...)

    SortOrder(String label, Comparator<Task> comparator) { // called once for each value above
        this.label = label;
        this.comparator = comparator;
    }

    public String getLabel() { return label; }
    public Comparator<Task> getComparator() { return comparator; }
}
