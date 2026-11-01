package studyboard.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** One study task, for example "Write HCI report". */
public class Task {
    private final int id;
    private String title;
    private String notes;
    private int subjectId;          // links to Subject.getId()
    private Status status;
    private Priority priority;
    private LocalDate deadline;     // LocalDate = a date without time
    private double estimatedHours;  // how long we expect the task to take

    public Task(int id, String title, int subjectId, Priority priority,
                LocalDate deadline, double estimatedHours) {
        this.id = id;
        setTitle(title);
        setPriority(priority);
        setDeadline(deadline);
        setEstimatedHours(estimatedHours);
        this.subjectId = subjectId;
        this.notes = "";
        this.status = Status.TODO; // every new task starts in "To do"
    }

    // ---- Business logic (no UI here) ----

    /** Days from 'today' until the deadline. Negative = overdue. */
    public long daysUntilDeadline(LocalDate today) {
        return ChronoUnit.DAYS.between(today, deadline);
    }

    public boolean isOverdue(LocalDate today) {
        return status != Status.DONE && daysUntilDeadline(today) < 0;
    }

    // ---- Getters and setters (setters validate the data) ----

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public int getSubjectId() { return subjectId; }
    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public LocalDate getDeadline() { return deadline; }
    public double getEstimatedHours() { return estimatedHours; }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty.");
        }
        this.title = title.trim();
    }

    public void setNotes(String notes) {
        this.notes = (notes == null) ? "" : notes; // null becomes empty text
    }

    public void setSubjectId(int subjectId) { this.subjectId = subjectId; }

    public void setStatus(Status status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null.");
        }
        this.status = status;
    }

    public void setPriority(Priority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("Priority cannot be null.");
        }
        this.priority = priority;
    }

    public void setDeadline(LocalDate deadline) {
        if (deadline == null) {
            throw new IllegalArgumentException("Deadline is required.");
        }
        this.deadline = deadline;
    }

    public void setEstimatedHours(double hours) {
        if (hours < 0) {
            throw new IllegalArgumentException("Estimated hours cannot be negative.");
        }
        this.estimatedHours = hours;
    }

    @Override
    public String toString() {
        return "[" + status.getLabel() + "] " + title + " (due " + deadline + ")";
    }
}