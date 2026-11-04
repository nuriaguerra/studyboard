package studyboard.model;

import java.util.*; // Comparator

/**
 * Formas de ordenar el tablero. Un Comparator es "una regla para decidir quién va antes".
 * thenComparing = desempate: si empatan en lo primero, mira lo siguiente.
 * El último desempate es siempre el id, así el orden es estable.
 */
public enum SortOrder {
    // Por fecha límite; si empatan, la prioridad más alta primero; si no, por id
    DEADLINE("Deadline",
            Comparator.comparing(Task::getDeadline)
                    .thenComparing(Task::getPriority, Comparator.<Priority>reverseOrder())
                    .thenComparingInt(Task::getId)),
    // Por prioridad (alta primero); si empatan, fecha más cercana; si no, id
    PRIORITY("Priority",
            Comparator.comparing(Task::getPriority, Comparator.<Priority>reverseOrder())
                    .thenComparing(Task::getDeadline)
                    .thenComparingInt(Task::getId)),
    // Alfabético sin distinguir mayúsculas; si empatan, id
    TITLE("Title",
            Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Task::getId));

    private final String label;               // texto para el usuario
    private final Comparator<Task> comparator; // la regla de ordenación

    SortOrder(String label, Comparator<Task> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    public String getLabel() { return label; }
    public Comparator<Task> getComparator() { return comparator; }
}
