package studyboard.model;

import java.time.*;          // LocalDate
import java.time.temporal.*; // ChronoUnit (para contar días)

/** Una tarea de estudio, por ejemplo "Write HCI report". Solo datos + lógica, sin pantalla. */
public class Task {
    private final int id;            // único
    private String title;
    private String notes;
    private int subjectId;           // enlaza con Subject.getId()
    private Status status;           // columna del Kanban
    private Priority priority;
    private LocalDate deadline;      // LocalDate = fecha sin hora
    private double estimatedHours;   // horas que calculamos que llevará

    public Task(int id, String title, int subjectId, Priority priority,
                LocalDate deadline, double estimatedHours) {
        this.id = id;
        setTitle(title);                   // los setters validan los datos
        setPriority(priority);
        setDeadline(deadline);
        setEstimatedHours(estimatedHours);
        this.subjectId = subjectId;
        this.notes = "";                   // sin notas al principio
        this.status = Status.TODO;         // toda tarea nueva empieza en "To do"
    }

    // ---- Lógica (sin pantalla) ----

    /** Días desde 'today' hasta la fecha límite. Negativo = ya pasó. */
    public long daysUntilDeadline(LocalDate today) {
        return ChronoUnit.DAYS.between(today, deadline);
    }

    /** Atrasada = no terminada Y fecha ya pasada. */
    public boolean isOverdue(LocalDate today) {
        return status != Status.DONE && daysUntilDeadline(today) < 0;
    }

    // ---- Getters (leer) ----
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public int getSubjectId() { return subjectId; }
    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public LocalDate getDeadline() { return deadline; }
    public double getEstimatedHours() { return estimatedHours; }

    // ---- Setters (cambiar, con validación: lanzan error si el dato es malo) ----
    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty.");
        }
        this.title = title.trim();
    }

    public void setNotes(String notes) {
        this.notes = (notes == null) ? "" : notes; // si es null, lo cambiamos por texto vacío
    }

    public void setSubjectId(int subjectId) { this.subjectId = subjectId; }

    public void setStatus(Status status) {
        if (status == null) throw new IllegalArgumentException("Status cannot be null.");
        this.status = status;
    }

    public void setPriority(Priority priority) {
        if (priority == null) throw new IllegalArgumentException("Priority cannot be null.");
        this.priority = priority;
    }

    public void setDeadline(LocalDate deadline) {
        if (deadline == null) throw new IllegalArgumentException("Deadline is required.");
        this.deadline = deadline;
    }

    public void setEstimatedHours(double hours) {
        if (hours < 0) throw new IllegalArgumentException("Estimated hours cannot be negative.");
        this.estimatedHours = hours;
    }

    @Override
    public String toString() {
        return "[" + status.getLabel() + "] " + title + " (due " + deadline + ")";
    }
}
