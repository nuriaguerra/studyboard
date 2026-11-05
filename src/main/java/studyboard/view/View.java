package studyboard.view;

import java.time.*;   // LocalDate
import java.util.*;   // List, Map
import studyboard.model.*;

/**
 * Interface is a list of methods that a screen must have. There is no code here, only the names.
 * The Controller only knows this interface, not Lanterna.
 */
public interface View {
    /** Registers who must be told about the user's actions (the Controller). */
    void setListener(ViewListener listener);

    /**
     * Draws the Kanban board with the tasks to show (already filtered and sorted).
     */
    void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today, BoardOptions options);

    /** Shows the Today screen. 'ranked' comes sorted: the first item is the suggestion. */
    void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today);

    /**
     * Shows the Subjects screen. taskCounts: subjectId -> number of tasks.
     * If the screen is already open, it only refreshes the list and returns immediately.
     */
    void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts);

    /** Short feedback line for the user ("visibility of system status"). */
    void showMessage(String message);

    /** Opens the interface and waits until the user quits. */
    void run();

    /** Monthly calendar. It receives all the tasks: the board filter does not hide deadlines. */
    void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today);
}
