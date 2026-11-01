package studyboard.view.lanterna;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import studyboard.model.Status;
import studyboard.model.Task;
import studyboard.view.View;
import studyboard.view.ViewListener;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import studyboard.model.Priority;
import studyboard.model.TaskData;

/** Text mode implementation of View using Lanterna. All the drawing code lives here. */
public class LanternaView implements View {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // STRICT + "uuuu" = 31.02.2026 is rejected instead of silently changed to 28.02.2026.
    private static final DateTimeFormatter DATE_INPUT = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final boolean useSwing;                // true = separate emulator window
    private final BasicWindow window = new BasicWindow("StudyBoard - Board");
    private ViewListener listener;
    private MultiWindowTextGUI gui;                // created in run()

    // State of the board currently on screen
    private final ActionListBox[] lists = new ActionListBox[Status.values().length]; // one per column
    private final List<List<Task>> columnTasks = new ArrayList<>();                  // tasks per column
    private Label statusLine = new Label(" ");                                       // feedback line
    private Map<Integer, String> subjectNames = Map.of();
    private LocalDate today = LocalDate.now();
    private Integer pendingSelectId;               // task to re-select after redrawing (null = none)

    public LanternaView(boolean useSwing) {
        this.useSwing = useSwing;
        window.setHints(List.of(Window.Hint.FULL_SCREEN)); // use the whole terminal

        // Global keys. onInput is called BEFORE the focused component sees the key,
        // so we only react to our own letters and leave arrows/Enter/Tab alone.
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                if (keyStroke.getKeyType() != KeyType.Character
                        || keyStroke.isCtrlDown() || keyStroke.isAltDown()) {
                    return; // not a plain letter: ignore
                }
                switch (Character.toLowerCase(keyStroke.getCharacter())) {
                    case 'q' -> { deliverEvent.set(false); window.close(); }
                    case 'm' -> { deliverEvent.set(false); moveSelectedTask(+1); }
                    case 'b' -> { deliverEvent.set(false); moveSelectedTask(-1); }
                    case 'n' -> { deliverEvent.set(false); showTaskForm(null); }
                    case 'e' -> { deliverEvent.set(false); editSelectedTask(); }
                    case 'd' -> { deliverEvent.set(false); deleteSelectedTask(); }
                    default -> { } // other letters: do nothing for now
                }
            }
        });
    }

    @Override
    public void setListener(ViewListener listener) {
        this.listener = listener;
    }

    @Override
    public void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today) {
        this.subjectNames = subjectNames;
        this.today = today;

        Status[] statuses = Status.values();
        columnTasks.clear();

        // One bordered column per status, laid out side by side (grid with 3 columns).
        Panel board = new Panel(new GridLayout(statuses.length));
        for (int i = 0; i < statuses.length; i++) {
            List<Task> inColumn = new ArrayList<>();
            ActionListBox list = new ActionListBox(new TerminalSize(28, 12)); // preferred size
            for (Task task : tasks) {
                if (task.getStatus() == statuses[i]) {
                    inColumn.add(task);
                    // The Runnable runs when the user presses Enter on this item.
                    list.addItem(formatTask(task), () -> showDetails(task));
                }
            }
            columnTasks.add(inColumn);
            lists[i] = list;

            String title = statuses[i].getLabel() + " (" + inColumn.size() + ")";
            Border column = list.withBorder(Borders.singleLine(title));
            // FILL + true,true = the column grows to use all free space
            column.setLayoutData(GridLayout.createLayoutData(
                    GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, true));
            board.addComponent(column);
        }

        // Whole screen content, top to bottom (default layout of Panel is vertical).
        Panel root = new Panel();
        root.addComponent(new Label("Today: " + today.format(LONG_DATE) + "   |   Sort: deadline"));
        board.setLayoutData(LinearLayout.createLayoutData(
                LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow));
        root.addComponent(board);
        root.addComponent(new Label("Arrows: move | Enter: details | N: new | E: edit | D: delete | M/B: move | Q: quit"));
        statusLine = new Label(" ");
        root.addComponent(statusLine);
        window.setComponent(root);

        restoreSelection();
    }

    @Override
    public void showMessage(String message) {
        statusLine.setText(message);
    }

    @Override
    public void run() {
        Screen screen = null;
        try {
            // Real terminal by default; "--swing" opens a separate emulator window.
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setForceTextTerminal(!useSwing);
            factory.setPreferTerminalEmulator(useSwing);
            Terminal terminal = factory.createTerminal();

            screen = new TerminalScreen(terminal);
            screen.startScreen();
            gui = new MultiWindowTextGUI(screen);
            gui.addWindowAndWait(window); // blocks until window.close()
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start the text user interface", e);
        } finally {
            if (screen != null) {
                try {
                    screen.stopScreen(); // always restore the normal terminal
                } catch (IOException ignored) { }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** One line per task. Priority symbol + LATE marker so colour is never the only signal. */
    private String formatTask(Task task) {
        String subject = subjectNames.getOrDefault(task.getSubjectId(), "?");
        String late = task.isOverdue(today) ? "LATE " : "";
        return String.format("%-2s %s%s (%s, %s)", task.getPriority().getSymbol(), late,
                task.getTitle(), subject, task.getDeadline().format(SHORT_DATE));
    }

    /** Dialog with all the info of a task (Enter key). */
    private void showDetails(Task task) {
        long days = task.daysUntilDeadline(today);
        String when = days < 0 ? Math.abs(days) + " day(s) overdue"
                : days == 0 ? "due today" : "due in " + days + " day(s)";
        String text = "Title:     " + task.getTitle()
                + "\nSubject:   " + subjectNames.getOrDefault(task.getSubjectId(), "?")
                + "\nStatus:    " + task.getStatus().getLabel()
                + "\nPriority:  " + task.getPriority().getLabel()
                + "\nDeadline:  " + task.getDeadline().format(LONG_DATE) + " (" + when + ")"
                + "\nEstimate:  " + task.getEstimatedHours() + " h"
                + "\nNotes:     " + (task.getNotes().isEmpty() ? "-" : task.getNotes());
        MessageDialog.showMessageDialog(gui, "Task details", text);
    }

    /** Task currently highlighted in the focused column, or null if none. */
    private Task selectedTask() {
        Interactable focused = window.getFocusedInteractable();
        for (int i = 0; i < lists.length; i++) {
            if (focused == lists[i]) {
                int index = lists[i].getSelectedIndex();
                if (index >= 0 && index < columnTasks.get(i).size()) {
                    return columnTasks.get(i).get(index);
                }
            }
        }
        return null;
    }

    /** direction +1 = next column, -1 = previous column. Asks the Controller to do the change. */
    private void moveSelectedTask(int direction) {
        Task task = selectedTask();
        if (task == null) {
            showMessage("Select a task first (arrow keys).");
            return;
        }
        int target = task.getStatus().ordinal() + direction;
        Status[] all = Status.values();
        if (target < 0 || target >= all.length) {
            showMessage("The task is already in the " + (direction > 0 ? "last" : "first") + " column.");
            return;
        }
        pendingSelectId = task.getId();                 // so the cursor follows the task
        listener.onMoveTask(task.getId(), all[target]); // Controller changes Model and redraws
    }

    /** After a redraw, put the cursor back on the task that was just moved. */
    private void restoreSelection() {
        if (pendingSelectId != null) {
            for (int c = 0; c < columnTasks.size(); c++) {
                for (int i = 0; i < columnTasks.get(c).size(); i++) {
                    if (columnTasks.get(c).get(i).getId() == pendingSelectId) {
                        window.setFocusedInteractable(lists[c]);
                        lists[c].setSelectedIndex(i);
                    }
                }
            }
            pendingSelectId = null;
        }
    }

        // ------------------------------------------------------------ create / edit / delete

    private void editSelectedTask() {
        Task task = selectedTask();
        if (task == null) {
            showMessage("Select a task first (arrow keys).");
            return;
        }
        showTaskForm(task);
    }

    /** Asks for confirmation (error prevention) and then tells the Controller to delete. */
    private void deleteSelectedTask() {
        Task task = selectedTask();
        if (task == null) {
            showMessage("Select a task first (arrow keys).");
            return;
        }
        // "No" is listed first so the safe answer is the first button.
        MessageDialogButton answer = MessageDialog.showMessageDialog(gui, "Delete task",
                "Delete \"" + task.getTitle() + "\"?", MessageDialogButton.No, MessageDialogButton.Yes);
        if (answer == MessageDialogButton.Yes) {
            listener.onDeleteTask(task.getId());
        } else {
            showMessage("Delete cancelled.");
        }
    }

    /** Form window. existing == null means "new task", otherwise "edit this task". */
    private void showTaskForm(Task existing) {
        if (subjectNames.isEmpty()) {
            showMessage("There are no subjects yet.");
            return;
        }
        boolean editing = existing != null;
        BasicWindow form = new BasicWindow(editing ? "Edit task" : "New task");
        form.setHints(List.of(Window.Hint.CENTERED));

        // Same order in both lists (the map keeps insertion order).
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> subjectLabels = new ArrayList<>(subjectNames.values());
        List<String> priorityLabels = new ArrayList<>();
        for (Priority p : Priority.values()) {
            priorityLabels.add(p.getLabel());
        }

        // Input fields, pre-filled when editing. Enter moves to the next field.
        TextBox title = new TextBox(new TerminalSize(40, 1), editing ? existing.getTitle() : "");
        ComboBox<String> subject = new ComboBox<String>(subjectLabels);
        ComboBox<String> priority = new ComboBox<String>(priorityLabels);
        LocalDate defaultDate = editing ? existing.getDeadline() : today.plusDays(7);
        TextBox date = new TextBox(new TerminalSize(14, 1), defaultDate.format(LONG_DATE));
        TextBox hours = new TextBox(new TerminalSize(8, 1),
                editing ? String.valueOf(existing.getEstimatedHours()) : "1.0");
        TextBox notes = new TextBox(new TerminalSize(40, 1), editing ? existing.getNotes() : "");

        if (editing) {
            int subjectIndex = subjectIds.indexOf(existing.getSubjectId());
            if (subjectIndex >= 0) {
                subject.setSelectedIndex(subjectIndex);
            }
            priority.setSelectedIndex(existing.getPriority().ordinal());
        } else {
            priority.setSelectedIndex(Priority.MEDIUM.ordinal());
        }

        Button save = new Button("Save", () -> {
            try {
                // Building TaskData validates everything; errors come as IllegalArgumentException.
                TaskData data = new TaskData(title.getText(), notes.getText(),
                        subjectIds.get(subject.getSelectedIndex()),
                        Priority.values()[priority.getSelectedIndex()],
                        parseDate(date.getText()), parseHours(hours.getText()));
                if (editing) {
                    pendingSelectId = existing.getId(); // keep the cursor on the edited task
                    listener.onEditTask(existing.getId(), data);
                } else {
                    listener.onAddTask(data);
                }
                form.close();
            } catch (IllegalArgumentException e) {
                // The form stays open so the user can fix the problem.
                MessageDialog.showMessageDialog(gui, "Please check the form", e.getMessage());
            }
        });
        Button cancel = new Button("Cancel", form::close);

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));
        buttons.addComponent(save);
        buttons.addComponent(cancel);
        buttons.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false, 2, 1));

        Panel panel = new Panel(new GridLayout(2));
        panel.addComponent(new Label("Title:"));
        panel.addComponent(title);
        panel.addComponent(new Label("Subject:"));
        panel.addComponent(subject);
        panel.addComponent(new Label("Priority:"));
        panel.addComponent(priority);
        panel.addComponent(new Label("Deadline (DD.MM.YYYY):"));
        panel.addComponent(date);
        panel.addComponent(new Label("Estimate (hours):"));
        panel.addComponent(hours);
        panel.addComponent(new Label("Notes (optional):"));
        panel.addComponent(notes);
        panel.addComponent(new Label("Tab: next field | Esc: cancel"));
        panel.addComponent(new EmptySpace());
        panel.addComponent(buttons);
        form.setComponent(panel);

        // Esc cancels, like in every other screen. onUnhandledInput only receives keys
        // that no component used, so Esc still closes an open drop-down list normally.
        form.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    form.close();
                    hasBeenHandled.set(true);
                }
            }
        });

        gui.addWindowAndWait(form); // blocks until the form closes
    }

    /** Text to date, with a message that says exactly what is wrong. */
    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_INPUT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The deadline must be a real date written as DD.MM.YYYY, for example "
                    + today.plusDays(7).format(LONG_DATE) + ".");
        }
    }

    /** Text to number of hours. Accepts "2.5" and "2,5". */
    private double parseHours(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The estimate must be a number of hours, for example 2.5.");
        }
    }
}