package studyboard.view.swing;

import com.formdev.flatlaf.FlatDarkLaf;   // dark theme
import com.formdev.flatlaf.FlatLightLaf;  // light theme
import java.awt.*;                         // BorderLayout, GridLayout, Font, Dimension, Component, Window
import java.awt.datatransfer.*;            // StringSelection, Transferable, DataFlavor (used by drag and drop)
import java.awt.event.*;                   // MouseAdapter, MouseEvent
import java.time.*;                        // LocalDate
import java.time.format.*;                 // DateTimeFormatter
import java.util.*;                        // Map, ArrayList
import java.util.List;                     // java.awt and java.util BOTH have a "List": we mean java.util's
import javax.swing.*;                      // JFrame, JPanel, JList, JButton, JLabel...
import studyboard.model.*;
import studyboard.model.SortOrder;         // javax.swing ALSO has a SortOrder: this says we mean ours
import studyboard.view.*;

/**
 * VIEW with a graphical window (Swing). It implements the SAME interface as LanternaView,
 * so the Controller does not know (and does not care) which one it is talking to.
 * Rule that never changes: the View does not change data. It only calls listener.onXxx(...)
 * (asks the Controller) and draws what the Controller sends with showXxx(...).
 *
 * The secondary windows are in their own classes: TaskFormDialog, FilterDialog, TodayDialog,
 * SubjectsDialog and CalendarDialog. This class has the main window (the Kanban board).
 */
public class SwingView implements View {
    private ViewListener listener; // the Controller (the View asks it to do things)
    // The last data sent by the Controller. The forms start from them.
    private BoardOptions options = new BoardOptions(TaskFilter.NONE, SortOrder.DEADLINE, 0);
    private Map<Integer, String> subjectNames = Map.of();
    private LocalDate today = LocalDate.now();
    private Integer pendingSelectId; // task to select again after redrawing (null = none), same idea as in text mode
    private boolean dark = false;    // is the dark theme on?

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
    private final JButton themeButton = new JButton("Dark mode");
    private SubjectsDialog subjectsDialog;                        // null while the Subjects window is closed

    public SwingView() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // closing the window ends the program
        frame.setSize(1100, 700);
        frame.setMinimumSize(new Dimension(800, 500)); // the window cannot be made smaller than this
        frame.setLocationRelativeTo(null);             // null = centre on the screen

        // ---- the three columns ----
        JPanel board = new JPanel(new GridLayout(1, statuses.length, 12, 0)); // 1 row, 3 columns: they share the width
        board.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        for (int c = 0; c < statuses.length; c++) {
            DefaultListModel<Task> model = new DefaultListModel<>();
            
            JList<Task> list = new JList<>(model) {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (getModel().getSize() == 0) {      // empty column: show a hint
                        g.setColor(Color.GRAY);
                        g.drawString("No tasks here", 14, 26);
                    }
                }
            };
            // The list uses Ctrl+Left/Right itself: remove them so the window shortcut works
            for (String k : new String[] {"ctrl LEFT", "ctrl RIGHT", "ctrl KP_LEFT", "ctrl KP_RIGHT"}) {
                list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(k), "none");
            }

            list.setCellRenderer(renderer);                             // every row is drawn as a card
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // only one task at a time
            // Selecting a task in this column clears the other columns: ONE selected task in the whole board
            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
                    clearOtherSelections(list);
                }
            });
            // Double-click = details
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                        showDetails(list.getSelectedValue());
                    }
                }
            });
            installDragAndDrop(list, c);

            JLabel header = new JLabel(statuses[c].getLabel());
            header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
            JPanel column = new JPanel(new BorderLayout(0, 6)); // header on top, list below
            column.add(header, BorderLayout.NORTH);
            column.add(new JScrollPane(list), BorderLayout.CENTER); // adds scroll bars when needed
            board.add(column);

            models.add(model);
            lists.add(list);
            headers.add(header);
        }
        applyColumnColors();

        // ---- toolbar 1: what you do with tasks ----
        JToolBar actions = new JToolBar();
        actions.setFloatable(false); // the user cannot drag the toolbar out of the window
        actions.add(button("New", "New task (Ctrl+N)", this::newTask));
        actions.add(button("Edit", "Edit the selected task (Ctrl+E)", this::editSelected));
        actions.add(button("Delete", "Delete the selected task (Delete)", this::deleteSelected));
        actions.addSeparator();
        actions.add(button("← Move left", "Move to the previous column (Ctrl+Left)", () -> moveSelected(-1)));
        actions.add(button("Move right →", "Move to the next column (Ctrl+Right)", () -> moveSelected(+1)));
        actions.addSeparator();
        actions.add(button("Undo", "Undo the last change (Ctrl+Z)", () -> listener.onUndo()));
        actions.addSeparator();
        actions.add(button("Today", "What to do next (F2)", () -> listener.onShowToday()));
        actions.add(button("Subjects", "Create, rename, delete subjects (F3)", () -> listener.onShowSubjects()));
        actions.add(button("Calendar", "Deadlines by month (F4)", () -> listener.onShowCalendar()));

        // ---- toolbar 2: how you see tasks ----
        searchField.setMaximumSize(searchField.getPreferredSize()); // do not let the box stretch
        searchField.putClientProperty("JTextField.placeholderText", "Search title, notes, subject… (Enter)"); // grey hint (FlatLaf)
        searchField.setToolTipText("Search (Ctrl+F). Press Enter to apply.");
        searchField.addActionListener(e ->   // Enter inside the box
                listener.onChangeView(options.filter().withText(searchField.getText()), options.sort()));
        themeButton.setToolTipText("Switch between light and dark theme");
        themeButton.addActionListener(e -> toggleTheme());
        JToolBar view = new JToolBar();
        view.setFloatable(false);
        view.add(searchField);
        view.add(button("Filter…", "Search, filter and sort", this::showFilter));
        view.add(button("Clear filter", "Show every task again", () -> listener.onChangeView(TaskFilter.NONE, options.sort())));
        view.add(Box.createHorizontalGlue()); // pushes what follows to the right edge
        view.add(button("Help", "Help of this window (F1)", () -> Ui.showText(frame, "Help - Board", HelpTexts.BOARD)));
        view.add(themeButton);

        // ---- information lines under the toolbars ----
        infoLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 0, 12));
        filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD)); // the active filter is written in bold
        filterLabel.setBorder(BorderFactory.createEmptyBorder(2, 12, 0, 12));
        filterLabel.setVisible(false);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS)); // stacked vertically
        for (JComponent c : new JComponent[] {actions, view, infoLabel, filterLabel}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.add(c);
        }
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        frame.add(top, BorderLayout.NORTH);
        frame.add(board, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);

        // ---- keyboard shortcuts (they work in the whole window) ----
        JRootPane root = frame.getRootPane();
        Ui.bindKey(root, "ctrl N", this::newTask);
        Ui.bindKey(root, "ctrl E", this::editSelected);
        Ui.bindKey(root, "DELETE", this::deleteSelected);
        Ui.bindKey(root, "ctrl Z", () -> listener.onUndo());
        Ui.bindKey(root, "ctrl F", searchField::requestFocusInWindow);
        Ui.bindKey(root, "ctrl LEFT", () -> moveSelected(-1));
        Ui.bindKey(root, "ctrl RIGHT", () -> moveSelected(+1));
        Ui.bindKey(root, "F2", () -> listener.onShowToday());
        Ui.bindKey(root, "F3", () -> listener.onShowSubjects());
        Ui.bindKey(root, "F4", () -> listener.onShowCalendar());
        Ui.helpOnF1(root, frame, "Help - Board", HelpTexts.BOARD);
    }

    /** Creates a toolbar button that runs 'action' when pressed. */
    private JButton button(String text, String tooltip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip); // text that appears when the mouse rests on the button
        b.addActionListener(e -> action.run());
        return b;
    }

    @Override
    public void setListener(ViewListener listener) {
        this.listener = listener; // the Controller registers itself here
    }

    // ================================================================== View interface

    @Override
    public void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today, BoardOptions options) {
        this.options = options;
        this.subjectNames = subjectNames;
        this.today = today;
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

        infoLabel.setText("Today: " + today.format(Ui.LONG_DATE) + "   |   Sort: " + options.sort().getLabel()
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
        if (subjectsDialog != null && subjectsDialog.isDisplayable()) {
            subjectsDialog.setStatus(message); // also inside the Subjects window, if it is open
        }
    }

    @Override
    public void run() {
        // The Controller calls run() last. In Swing it does NOT block: it only makes the window visible.
        // The program stays alive because the Swing thread keeps running until the window is closed.
        frame.setVisible(true);
    }

    @Override
    public void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today) {
        // this::moveTask = "call my moveTask method": the dialog uses it to ask for a move
        new TodayDialog(frame, ranked, subjectNames, today, this::moveTask).setVisible(true); // modal: waits until closed
    }

    @Override
    public void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts) {
        // Called to OPEN the window and also to REFRESH it after every change
        if (subjectsDialog == null || !subjectsDialog.isDisplayable()) { // not open (isDisplayable is false after dispose)
            subjectsDialog = new SubjectsDialog(frame, listener);
            subjectsDialog.refill(subjects, taskCounts);
            subjectsDialog.setVisible(true); // not modal: this call returns immediately
        } else {
            subjectsDialog.refill(subjects, taskCounts);
        }
    }

    @Override
    public void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today) {
        new CalendarDialog(frame, tasks, subjectNames, today).setVisible(true);
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

    /** The selected task; if there is none, it warns the user and returns null. */
    private Task selectedTaskOrWarn() {
        Task task = selectedTask();
        if (task == null) {
            showMessage("Select a task first (click on a card).");
        }
        return task;
    }

    private Task findTask(int id) {
        for (DefaultListModel<Task> model : models) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).getId() == id) {
                    return model.get(i);
                }
            }
        }
        return null;
    }

    /** direction +1 = next column, -1 = previous column. */
    private void moveSelected(int direction) {
        Task task = selectedTaskOrWarn();
        if (task == null) return;
        int target = task.getStatus().ordinal() + direction;
        if (target < 0 || target >= statuses.length) {
            showMessage("The task is already in the " + (direction > 0 ? "last" : "first") + " column.");
            return;
        }
        moveTask(task, statuses[target]);
    }

    /** The only place where the View asks for a move (buttons, keys, Today and drag and drop all use it). */
    private void moveTask(Task task, Status target) {
        pendingSelectId = task.getId();                  // the selection will follow the task
        listener.onMoveTask(task.getId(), target);       // the Controller changes the Model and redraws
    }

    /** After redrawing, selects again the task that was moved or edited. */
    private void restoreSelection() {
        if (pendingSelectId == null) return;
        for (int c = 0; c < models.size(); c++) {
            for (int i = 0; i < models.get(c).size(); i++) {
                if (models.get(c).get(i).getId() == pendingSelectId) {
                    lists.get(c).setSelectedIndex(i);     // this also clears the other columns (listener above)
                    lists.get(c).ensureIndexIsVisible(i); // scrolls if the card is outside the visible area
                }
            }
        }
        pendingSelectId = null;
    }

    // ================================================================== create / edit / delete / details

    private void newTask() {
        if (subjectNames.isEmpty()) { // a task needs a subject
            showMessage("There are no subjects yet. Open Subjects (F3) to create one.");
            return;
        }
        openForm(null);
    }

    private void editSelected() {
        Task task = selectedTaskOrWarn();
        if (task != null) openForm(task);
    }

    /** existing == null = new task. The lambda runs when the form is saved with VALID data. */
    private void openForm(Task existing) {
        new TaskFormDialog(frame, existing, subjectNames, today, data -> {
            if (existing != null) {
                pendingSelectId = existing.getId();               // keep the selection on the edited task
                listener.onEditTask(existing.getId(), data);
            } else {
                listener.onAddTask(data);
            }
        }).setVisible(true);
    }

    private void deleteSelected() {
        Task task = selectedTaskOrWarn();
        if (task == null) return;
        if (Ui.confirm(frame, "Delete task", "Delete \"" + task.getTitle() + "\"?\nYou can undo it with Undo.")) {
            listener.onDeleteTask(task.getId()); // the View never deletes anything, it asks the Controller
        } else {
            showMessage("Delete cancelled.");
        }
    }

    private void showDetails(Task task) {
        long days = task.daysUntilDeadline(today);
        String when = days < 0 ? Math.abs(days) + " day(s) overdue"
                : days == 0 ? "due today" : "due in " + days + " day(s)";
        Ui.showText(frame, "Task details",
                "Title:     " + task.getTitle()
                + "\nSubject:   " + subjectNames.getOrDefault(task.getSubjectId(), "?")
                + "\nStatus:    " + task.getStatus().getLabel()
                + "\nPriority:  " + task.getPriority().getLabel()
                + "\nDeadline:  " + task.getDeadline().format(Ui.LONG_DATE) + " (" + when + ")"
                + "\nEstimate:  " + task.getEstimatedHours() + " h"
                + "\nNotes:     " + (task.getNotes().isEmpty() ? "-" : task.getNotes()));
    }

    private void showFilter() {
        new FilterDialog(frame, listener, options, subjectNames).setVisible(true);
    }

    // ================================================================== polish: drag and drop, theme

    /**
     * Lets the user drag a card from this list and drop it on another column.
     * What travels is only the task id (as text). The drop target asks the Controller to move that task.
     */
    private void installDragAndDrop(JList<Task> list, int column) {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                Task task = list.getSelectedValue();
                return task == null ? null : new StringSelection(String.valueOf(task.getId()));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    String text = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    Task task = findTask(Integer.parseInt(text));
                    if (task == null) return false;
                    if (task.getStatus() != statuses[column]) { // dropping on its own column does nothing
                        moveTask(task, statuses[column]);
                    }
                    return true;
                } catch (Exception e) {
                    return false; // not one of our cards
                }
            }
        });
    }

    /** Columns are grey and cards are lighter: the colours come from the current theme. */
    private void applyColumnColors() {
        Color columnColor = UIManager.getColor("Panel.background");
        for (JList<Task> list : lists) {
            list.setBackground(columnColor);
            list.getParent().setBackground(columnColor); // the viewport (empty area below the cards)
        }
    }

    private void toggleTheme() {
        dark = !dark;
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w); // repaint every open window with the new theme
        }
        applyColumnColors();
        themeButton.setText(dark ? "Light mode" : "Dark mode");
        frame.repaint();
    }
}
