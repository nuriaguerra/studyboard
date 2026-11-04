package studyboard.model;

/**
 * Lo que el Controller le cuenta a la View sobre el tablero actual:
 * qué filtro hay, cómo se ordena y cuántas tareas existen en total.
 */
public record BoardOptions(TaskFilter filter, SortOrder sort, int totalTasks) { }
