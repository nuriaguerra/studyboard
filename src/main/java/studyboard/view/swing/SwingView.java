package studyboard.view.swing;

import java.awt.*;          // BorderLayout (how components are placed)
import java.time.*;         // LocalDate
import java.util.*;         // Map...
import java.util.List;      // IMPORTANT: java.awt and java.util BOTH have a "List"; this line says we mean java.util's
import javax.swing.*;       // JFrame, JLabel, BorderFactory...
import studyboard.model.*;
import studyboard.view.*;

/**
 * VIEW with a graphical window (Swing). It implements the SAME interface as LanternaView,
 * so the Controller does not know (and does not care) which one it is talking to.
 * Stage 1: an empty window that proves the connection. The real screens come in the next stages.
 */
public class SwingView implements View {
    private ViewListener listener; // the Controller (used from stage 2 on, when there are buttons)

    private final JFrame frame = new JFrame("StudyBoard");                       // the main window
    private final JLabel content = new JLabel("Loading...", SwingConstants.CENTER); // temporary centre text
    private final JLabel statusBar = new JLabel(" ");                            // bottom line for messages

    public SwingView() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // closing the window ends the program
        frame.setSize(1000, 650);          // width x height in pixels
        frame.setLocationRelativeTo(null); // null = centre the window on the screen
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10)); // some air around the text
        // A JFrame uses BorderLayout: it has CENTER, SOUTH (bottom), NORTH, EAST, WEST regions
        frame.add(content, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);
    }

    @Override
    public void setListener(ViewListener listener) {
        this.listener = listener; // the Controller registers itself here
    }

    @Override
    public void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today, BoardOptions options) {
        // The Controller sends the SAME data as in Project 1. For now we only show how many tasks arrived.
        content.setText("Board: " + tasks.size() + " of " + options.totalTasks() + " tasks");
    }

    @Override
    public void showMessage(String message) {
        statusBar.setText(message); // feedback line (same idea as the status line in text mode)
    }

    @Override
    public void run() {
        // The Controller calls run() last. In Swing it does NOT block: it only makes the window visible.
        // The program stays alive because the Swing thread keeps running until the window is closed.
        frame.setVisible(true);
    }

    // ---- Screens that are not built yet (stages 3 and 4). Empty on purpose. ----

    @Override
    public void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today) { }

    @Override
    public void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts) { }

    @Override
    public void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today) { }
}