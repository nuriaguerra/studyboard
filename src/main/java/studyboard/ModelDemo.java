package studyboard;

import java.time.LocalDate;
import studyboard.model.Priority;
import studyboard.model.Status;
import studyboard.model.Subject;
import studyboard.model.Task;

/** Temporary console check of the Model. It will be replaced by real unit tests. */
public class ModelDemo {
    public static void main(String[] args) {
        Subject hci = new Subject(1, "HCI");
        Task task = new Task(1, "Project 1: choose topic", hci.getId(),
                Priority.HIGH, LocalDate.now().plusDays(2), 3.0);

        System.out.println(task);
        System.out.println("Days left: " + task.daysUntilDeadline(LocalDate.now()));

        task.setStatus(Status.IN_PROGRESS);
        System.out.println(task);

        // Error prevention: an empty title must be rejected.
        try {
            task.setTitle("   ");
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected as expected: " + e.getMessage());
        }
    }
}