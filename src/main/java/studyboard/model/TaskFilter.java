package studyboard.model;

import java.util.*; // List, ArrayList, Locale, Map

/**
 * Lo que el usuario quiere ver en el tablero. null / vacío / false = "sin restricción".
 * Es un record: inmutable. Para cambiar el filtro se crea uno NUEVO.
 */
public record TaskFilter(String text, Integer subjectId, Priority priority, boolean hideDone) {
    // Integer (con mayúscula) puede ser null; int no. Aquí null = "todas las asignaturas".

    /** El filtro que deja pasar todo. static final = una sola copia compartida. */
    public static final TaskFilter NONE = new TaskFilter("", null, null, false);

    /** Constructor compacto: limpia el texto al crear el filtro. */
    public TaskFilter {
        text = (text == null) ? "" : text.trim();
    }

    /** ¿Hay alguna restricción puesta? */
    public boolean isActive() {
        return !text.isEmpty() || subjectId != null || priority != null || hideDone;
    }

    /** Una tarea se muestra solo si cumple TODAS las condiciones puestas. */
    public boolean matches(Task task, Map<Integer, String> subjectNames) {
        if (hideDone && task.getStatus() == Status.DONE) return false;               // ocultar terminadas
        if (subjectId != null && !subjectId.equals(task.getSubjectId())) return false; // otra asignatura
        if (priority != null && task.getPriority() != priority) return false;        // otra prioridad
        if (!text.isEmpty()) {
            String wanted = text.toLowerCase(Locale.ROOT); // ROOT = mismo resultado en cualquier PC
            String subject = subjectNames.getOrDefault(task.getSubjectId(), "");
            // Busca el texto en título, notas o nombre de asignatura (sin mayúsculas)
            boolean found = task.getTitle().toLowerCase(Locale.ROOT).contains(wanted)
                    || task.getNotes().toLowerCase(Locale.ROOT).contains(wanted)
                    || subject.toLowerCase(Locale.ROOT).contains(wanted);
            if (!found) return false;
        }
        return true; // pasó todas las pruebas
    }

    /** Mismo filtro pero con otro texto de búsqueda (lo usa la búsqueda rápida "/"). */
    public TaskFilter withText(String newText) {
        return new TaskFilter(newText, subjectId, priority, hideDone);
    }

    /** Resumen legible para la pantalla, p. ej.: search "lab", subject Physics. */
    public String describe(Map<Integer, String> subjectNames) {
        List<String> parts = new ArrayList<>();
        if (!text.isEmpty()) parts.add("search \"" + text + "\"");
        if (subjectId != null) parts.add("subject " + subjectNames.getOrDefault(subjectId, "?"));
        if (priority != null) parts.add("priority " + priority.getLabel());
        if (hideDone) parts.add("done tasks hidden");
        return parts.isEmpty() ? "All tasks" : String.join(", ", parts); // une con ", "
    }
}
