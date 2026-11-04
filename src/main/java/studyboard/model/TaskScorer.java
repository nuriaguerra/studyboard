package studyboard.model;

import java.time.*; // LocalDate
import java.util.*; // List, ArrayList, Comparator

/**
 * Decide "¿qué hago ahora?". Lógica pura: sin pantalla ni ficheros.
 * Es una tabla de puntos fácil de explicar: más puntos = hazla antes.
 */
public final class TaskScorer {
    private TaskScorer() { } // clase de utilidad: nadie crea objetos de ella

    /** Tareas pendientes (no Done), la mejor sugerencia primero. */
    public static List<Recommendation> rank(List<Task> tasks, LocalDate today) {
        List<Recommendation> result = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getStatus() != Status.DONE) { // las terminadas nunca se sugieren
                result.add(score(task, today));
            }
        }
        // Más puntos primero. Empate: fecha más cercana; después id más bajo.
        result.sort(Comparator.comparingInt(Recommendation::score).reversed()
                .thenComparing(r -> r.task().getDeadline())
                .thenComparingInt(r -> r.task().getId()));
        return result;
    }

    /** Da puntos a UNA tarea y apunta un motivo por cada regla que se aplicó. */
    public static Recommendation score(Task task, LocalDate today) {
        List<String> reasons = new ArrayList<>(); // motivos que verá el usuario
        int points = 0;
        long days = task.daysUntilDeadline(today); // negativo = atrasada

        // REGLA 1: cercanía de la fecha límite (siempre se aplica)
        if (days < 0) {
            points += 60;
            reasons.add("Overdue by " + daysText(-days));
        } else if (days == 0) {
            points += 55;
            reasons.add("Due today");
        } else {
            if (days <= 2) points += 45;
            else if (days <= 7) points += 30;
            else if (days <= 14) points += 15;
            else points += 5;
            reasons.add(days == 1 ? "Due tomorrow" : "Due in " + daysText(days));
        }

        // REGLA 2: prioridad elegida por el usuario
        switch (task.getPriority()) {
            case HIGH -> { points += 30; reasons.add("High priority"); }
            case MEDIUM -> { points += 15; reasons.add("Medium priority"); }
            case LOW -> { } // sin puntos ni motivo
        }

        // REGLA 3: esfuerzo. Una tarea grande con fecha cercana hay que empezarla ya.
        double hours = task.getEstimatedHours();
        if (hours >= 4 && days <= 7) {
            points += 10;
            reasons.add("Big task (" + hours + " h) with a close deadline: start early");
        } else if (hours <= 1) {
            points += 5;
            reasons.add("Quick win (about 1 hour or less)");
        }

        // REGLA 4: terminar lo empezado es mejor que empezar algo nuevo
        if (task.getStatus() == Status.IN_PROGRESS) {
            points += 5;
            reasons.add("Already in progress");
        }

        return new Recommendation(task, points, reasons);
    }

    /** "1 day" / "3 days" (singular o plural). */
    private static String daysText(long n) {
        return n + (n == 1 ? " day" : " days");
    }
}
