package studyboard.model;

import java.time.*;          // LocalDate
import java.time.temporal.*; // ChronoUnit (used to count days between two dates)

/** One study task, for example "Write HCI report". Only data + logic, nothing about the screen. */
public class Task {
    private final int id;            // unique number, it never changes
    private String title;
    private String notes;
    private int subjectId;           // links to Subject.getId() (we store the id, not the object)
    private Status status;           // the Kanban column the task is in
    private Priority priority;
    private LocalDate deadline;      // LocalDate = a date without a time of day
    private double estimatedHours;   // how many hours we expect the task to take

    public Task(int id, String title, int subjectId, Priority priority,
                LocalDate deadline, double estimatedHours) {
        this.id = id;
        setTitle(title);                   // the setters validate the data (they throw an error if it is wrong)
        setPriority(priority);
        setDeadline(deadline);
        setEstimatedHours(estimatedHours);
        this.subjectId = subjectId;
        this.notes = "";                   // no notes at the beginning
        this.status = Status.TODO;         // every new task starts in "To do"
    }

    // ---- Logic (no screen code here) ----

    /** Days from 'today' until the deadline. A negative number means it is already late. */
    public long daysUntilDeadline(LocalDate today) {
        // ChronoUnit.DAYS.between(a, b) counts the whole days that pass from a to b
        return ChronoUnit.DAYS.between(today, deadline);
    }

    /** Overdue = NOT finished AND the date has passed. A finished task is never overdue. */
    public boolean isOverdue(LocalDate today) {
        return status != Status.DONE && daysUntilDeadline(today) < 0;
    }

    // ---- Getters (read the data) ----
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public int getSubjectId() { return subjectId; }
    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public LocalDate getDeadline() { return deadline; }
    public double getEstimatedHours() { return estimatedHours; }

    // ---- Setters (change the data, with validation: they throw an error if the value is bad) ----
    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty.");
        }
        this.title = title.trim(); // remove the spaces around the title
    }

    public void setNotes(String notes) {
        this.notes = (notes == null) ? "" : notes; // if it is null we use an empty text instead
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
    public String toString() { // text version of the task, handy for debugging
        return "[" + status.getLabel() + "] " + title + " (due " + deadline + ")";
    }
}
