package studyboard.model;

import java.time.*; // LocalDate

/** Datos de ejemplo para que la app nunca arranque vacía (desarrollo y demo). */
public final class SampleData {
    private SampleData() { } // constructor privado: esta clase solo tiene métodos estáticos

    /** Rellena el repositorio con asignaturas y tareas de ejemplo. */
    public static void load(TaskRepository repo) {
        LocalDate today = LocalDate.now(); // fechas relativas a hoy: la demo siempre se ve bien
        Subject hci = repo.addSubject("HCI");
        Subject math = repo.addSubject("Math");
        Subject physics = repo.addSubject("Physics");
        Subject czech = repo.addSubject("Czech");

        // To do (una está ya atrasada a propósito: today.minusDays(1))
        repo.addTask("Project 1: choose topic", hci.getId(), Priority.HIGH, today.plusDays(2), 3);
        repo.addTask("Lab report", physics.getId(), Priority.MEDIUM, today.plusDays(4), 2.5);
        repo.addTask("Homework sheet 4", math.getId(), Priority.HIGH, today.minusDays(1), 2);
        repo.addTask("Prepare presentation", czech.getId(), Priority.LOW, today.plusDays(12), 1.5);

        // In progress: creamos la tarea y luego cambiamos su estado
        Task essay = repo.addTask("Math essay", math.getId(), Priority.MEDIUM, today.plusDays(7), 4);
        essay.setStatus(Status.IN_PROGRESS);
        Task homework = repo.addTask("Czech homework", czech.getId(), Priority.LOW, today.plusDays(6), 1);
        homework.setStatus(Status.IN_PROGRESS);

        // Done
        Task reading = repo.addTask("Read chapter 3", physics.getId(), Priority.LOW, today.minusDays(3), 1);
        reading.setStatus(Status.DONE);
        Task quiz = repo.addTask("Online quiz", hci.getId(), Priority.MEDIUM, today.minusDays(5), 0.5);
        quiz.setStatus(Status.DONE);
    }
}
