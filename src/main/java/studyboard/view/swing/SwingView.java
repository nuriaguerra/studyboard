package studyboard.view.swing;

import java.awt.*;   // BorderLayout (how components are placed)
import java.time.*;  // LocalDate
import java.time.format.*;  // DateTimeFormatter
import java.util.*;    // Map, ArrayList
import java.util.List;  // IMPORTANT: java.awt and java.util BOTH have a "List"; this line says we mean java.util's
import javax.swing.*;       // JFrame, JLabel, BorderFactory...
import studyboard.model.*;
import studyboard.model.SortOrder; // javax.swing ALSO has a SortOrder: this explicit import says we mean ours
import studyboard.view.*;


/**
 * VIEW with a graphical window (Swing). It implements the SAME interface as LanternaView,
 * so the Controller does not know (and does not care) which one it is talking to.
 * Rule that never changes: the View does not change data. It only calls listener.onXxx(...)
 * (asks the Controller) and draws what the Controller sends with showXxx(...).
 *
 * Stage 2: the Kanban board (three columns of cards), a toolbar, a search box and a status bar.
 */

public class SwingView implements View {
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ViewListener listener; // the Controller (the View asks it to do things)
    // The board options last sent by the Controller (filter, sort, total). The search box starts from them.
    private BoardOptions options = new BoardOptions(TaskFilter.NONE, SortOrder.DEADLINE, 0);
    private Integer pendingSelectId; // task to select again after redrawing (null = none), same idea as in text mode

    private final Status[] statuses = Status.values(); // one column per status: To do, In progress, Done
    // One entry per column, all in the same order as "statuses".
    private final List<DefaultListModel<Task>> models = new ArrayList<>(); // the data of each list
    private final List<JList<Task>> lists = new ArrayList<>();             // the visible lists
    private final List<JLabel> headers = new ArrayList<>();                // the titles "To do (3)"
    private final TaskCardRenderer renderer = new TaskCardRenderer();      // how a task looks as a card

    private final JFrame frame = new JFrame("StudyBoard");        // the main window
    private final JLabel infoLabel = new JLabel(" ");             // "Today: ... | Sort: ... | Showing 3 of 8"
    private final JLabel filterLabel = new JLabel(" ");           // "FILTER ON: ..." (only visible when a filter is active)
    private final JLabel statusBar = new JLabel(" ");             // bottom line for messages
    private final JTextField searchField = new JTextField(18);    // quick search

    public SwingView() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // closing the window ends the program
        frame.setSize(1100, 700);                 // width x height in pixels
        frame.setMinimumSize(new Dimension(800, 500)); // the window cannot be made smaller than this
        frame.setLocationRelativeTo(null);        // null = centre the window on the screen

        // ---- the three columns ----
        JPanel board = new JPanel(new GridLayout(1, statuses.length, 12, 0)); // 1 row, 3 columns, 12 px between them
        board.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        for (Status status : statuses) {
            DefaultListModel<Task> model = new DefaultListModel<>(); // the list of tasks of this column
            JList<Task> list = new JList<>(model);                   // the component that shows the model
            list.setCellRenderer(renderer);                          // every row is drawn as a card
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // only one task at a time
            list.setBackground(UIManager.getColor("Panel.background")); // grey column, white cards
            // When a task is selected in this column, we clear the selection of the other columns,
            // so there is only ONE selected task in the whole board.
            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
                    clearOtherSelections(list);
                }
            });

            JLabel header = new JLabel(status.getLabel());
            header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
            JPanel column = new JPanel(new BorderLayout(0, 6)); // header on top, list below
            column.add(header, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(list); // JScrollPane = adds scroll bars when needed
            scroll.getViewport().setBackground(UIManager.getColor("Panel.background")); // empty area below the cards
            column.add(scroll, BorderLayout.CENTER);
            board.add(column);

            models.add(model);
            lists.add(list);
            headers.add(header);
        }

        // ---- toolbar: move buttons, undo, search ----
        JButton back = new JButton("← Move left");
        back.addActionListener(e -> moveSelected(-1)); // lambda: runs when the button is pressed
        JButton forward = new JButton("Move right →");
        forward.addActionListener(e -> moveSelected(+1));
        JButton undo = new JButton("Undo");
        undo.addActionListener(e -> listener.onUndo());
        JButton clear = new JButton("Clear filter");
        clear.addActionListener(e -> listener.onChangeView(TaskFilter.NONE, options.sort()));
        // Pressing Enter inside the text field triggers an ActionEvent
        searchField.addActionListener(e ->
                listener.onChangeView(options.filter().withText(searchField.getText()), options.sort()));
        searchField.putClientProperty("JTextField.placeholderText", "Search title, notes, subject… (Enter)"); // grey hint (FlatLaf)

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false); // the user cannot drag the toolbar out of the window
        toolbar.add(back);
        toolbar.add(forward);
        toolbar.addSeparator();
        toolbar.add(undo);
        toolbar.addSeparator();
        toolbar.add(searchField);
        toolbar.add(clear);

        // ---- information lines under the toolbar ----
        infoLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 0, 12));
        filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD)); // the active filter is written in bold
        filterLabel.setBorder(BorderFactory.createEmptyBorder(2, 12, 0, 12));
        filterLabel.setVisible(false);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS)); // stacked vertically
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(toolbar);
        top.add(infoLabel);
        top.add(filterLabel);

        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        // A JFrame uses BorderLayout: NORTH (top), CENTER (middle), SOUTH (bottom)...
        frame.add(top, BorderLayout.NORTH);
        frame.add(board, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);
    }

    @Override
    public void setListener(ViewListener listener) {
        this.listener = listener; // the Controller registers itself here
    }

    // ================================================================== board

    @Override
    public void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today, BoardOptions options) {
        this.options = options;
        renderer.update(subjectNames, today); // so the cards can write the subject name and know what is late

        for (DefaultListModel<Task> model : models) {
            model.clear(); // we empty the three lists and fill them again (like the text mode board)
        }
        for (Task task : tasks) {
            models.get(task.getStatus().ordinal()).addElement(task); // ordinal() = 0, 1, 2 = the column
        }
        for (int i = 0; i < statuses.length; i++) {
            headers.get(i).setText(statuses[i].getLabel() + " (" + models.get(i).size() + ")");
        }

        infoLabel.setText("Today: " + today.format(LONG_DATE) + "   |   Sort: " + options.sort().getLabel()
                + "   |   Showing " + tasks.size() + " of " + options.totalTasks() + " tasks");
        filterLabel.setVisible(options.filter().isActive()); // BoxLayout skips invisible components
        filterLabel.setText("FILTER ON: " + options.filter().describe(subjectNames));
        searchField.setText(options.filter().text()); // keeps the box in sync (setText does not fire the Enter action)

        restoreSelection();
        frame.revalidate(); // recalculate the layout (the filter line may have appeared or disappeared)
        frame.repaint();
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

    // ================================================================== selection and movement

    /** Deselects every list except 'keep'. */
    private void clearOtherSelections(JList<Task> keep) {
        for (JList<Task> other : lists) {
            if (other != keep) {
                other.clearSelection();
            }
        }
    }

    /** The selected task of the whole board, or null. */
    private Task selectedTask() {
        for (JList<Task> list : lists) {
            Task task = list.getSelectedValue(); // null if nothing is selected in that list
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    /** direction +1 = next column, -1 = previous column. It asks the Controller to do the change. */
    private void moveSelected(int direction) {
        Task task = selectedTask();
        if (task == null) {
            showMessage("Select a task first (click on a card).");
            return;
        }
        int target = task.getStatus().ordinal() + direction;
        if (target < 0 || target >= statuses.length) {
            showMessage("The task is already in the " + (direction > 0 ? "last" : "first") + " column.");
            return;
        }
        pendingSelectId = task.getId();                     // the selection will follow the task
        listener.onMoveTask(task.getId(), statuses[target]); // the Controller changes the Model and redraws
    }

    /** After redrawing, selects again the task that was moved. */
    private void restoreSelection() {
        if (pendingSelectId == null) return;
        for (int c = 0; c < models.size(); c++) {
            for (int i = 0; i < models.get(c).size(); i++) {
                if (models.get(c).get(i).getId() == pendingSelectId) {
                    lists.get(c).setSelectedIndex(i);       // this also clears the other columns (listener above)
                    lists.get(c).ensureIndexIsVisible(i);   // scrolls if the card is outside the visible area
                }
            }
        }
        pendingSelectId = null;
    }

    // ---- Screens that are not built yet (stages 3 and 4). Empty on purpose. ----

    @Override
    public void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today) { }

    @Override
    public void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts) { }

    @Override
    public void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today) { }
}
