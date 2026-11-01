package studyboard.model;

import java.time.LocalDate;

/** Example data so the app is never empty during development and demos. */
public final class SampleData {
    private SampleData() { } // utility class: nobody should create objects of it

    public static void load(TaskRepository repo) {
        LocalDate today = LocalDate.now();
        Subject hci = repo.addSubject("HCI");
        Subject math = repo.addSubject("Math");
        Subject physics = repo.addSubject("Physics");
        Subject czech = repo.addSubject("Czech");

        // To do (one of them is already overdue on purpose)
        repo.addTask("Project 1: choose topic", hci.getId(), Priority.HIGH, today.plusDays(2), 3);
        repo.addTask("Lab report", physics.getId(), Priority.MEDIUM, today.plusDays(4), 2.5);
        repo.addTask("Homework sheet 4", math.getId(), Priority.HIGH, today.minusDays(1), 2);
        repo.addTask("Prepare presentation", czech.getId(), Priority.LOW, today.plusDays(12), 1.5);

        // In progress
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