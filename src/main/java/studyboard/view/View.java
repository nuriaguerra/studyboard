package studyboard.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import studyboard.model.Recommendation;
import studyboard.model.Task;
import studyboard.model.Subject;

/**
 * Everything the Controller may ask a user interface to do.
 * Project 1 implements it with Lanterna; Project 2 will implement it with a GUI.
 */
public interface View {
    /** Registers who should be told about user actions. */
    void setListener(ViewListener listener);

    /** Draws the Kanban board with these tasks. subjectNames maps subjectId -> name. */
    void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today);

    /** Shows the Today screen. 'ranked' is sorted: the first item is the suggestion. */
    void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today);

    /**
     * Shows the Subjects screen. taskCounts maps subjectId -> number of tasks.
     * If the screen is already open it only refreshes the list and returns immediately.
    */
    void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts);

    /** Short feedback line for the user (system status). */
    void showMessage(String message);

    /** Opens the UI and blocks until the user quits. */
    void run();
}