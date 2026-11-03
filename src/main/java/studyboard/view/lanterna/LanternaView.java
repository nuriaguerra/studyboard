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
import com.googlecode.lanterna.SGR;
import studyboard.model.Recommendation;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import java.util.function.Consumer;
import studyboard.model.Subject;

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
    // State of the Subjects screen. subjectsList is null while that screen is closed.
    private ActionListBox subjectsList;
    private Label subjectsStatus;
    private List<Subject> shownSubjects = new ArrayList<>();
    private Map<Integer, Integer> subjectTaskCounts = Map.of();

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
                    case 't' -> { deliverEvent.set(false); listener.onShowToday(); }
                    case 's' -> { deliverEvent.set(false); listener.onShowSubjects(); }
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
        root.addComponent(new Label("Arrows: move | Enter: details | N/E/D: new/edit/delete | M/B: move | T: Today | S: subjects | Q: quit"));
        statusLine = new Label(" ");
        root.addComponent(statusLine);
        window.setComponent(root);

        restoreSelection();
    }

    @Override
    public void showMessage(String message) {
        statusLine.setText(message);
        if (subjectsStatus != null) {   // the Subjects screen is open: show it there too
            subjectsStatus.setText(message);
        }
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
            gui.addWindow(window);      // show the board WITHOUT blocking...
            listener.onShowToday();     // ...open Today on top of it (blocks until it is closed)...
            window.waitUntilClosed();   // ...and then wait until the user quits the board
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
            showMessage("There are no subjects yet. Press S to create one.");
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

    // ------------------------------------------------------------------ Today screen

    @Override
    public void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today) {
        this.subjectNames = subjectNames; // formatTask() uses these two fields
        this.today = today;

        BasicWindow todayWindow = new BasicWindow("StudyBoard - Today");
        todayWindow.setHints(List.of(Window.Hint.CENTERED));

        Panel panel = new Panel();
        panel.addComponent(new Label("Today: " + today.format(LONG_DATE)));
        panel.addComponent(new EmptySpace());

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));

        if (ranked.isEmpty()) {
            panel.addComponent(new Label("Nothing pending. All your tasks are done!"));
        } else {
            Recommendation next = ranked.get(0);
            Task task = next.task();

            // The suggestion, in a bordered box.
            Panel card = new Panel();
            card.addComponent(new Label(task.getTitle()).addStyle(SGR.BOLD));
            card.addComponent(new Label(subjectNames.getOrDefault(task.getSubjectId(), "?")
                    + " | due " + task.getDeadline().format(LONG_DATE)
                    + " | " + task.getEstimatedHours() + " h"
                    + " | " + task.getPriority().getLabel() + " priority"));
            card.addComponent(new EmptySpace());
            card.addComponent(new Label("Why now?").addStyle(SGR.BOLD));
            for (String reason : next.reasons()) {
                card.addComponent(new Label("  - " + reason));
            }
            card.addComponent(new Label("Score: " + next.score()));
            panel.addComponent(card.withBorder(Borders.singleLine("Next up")));

            // The next 4 suggestions, one line each.
            if (ranked.size() > 1) {
                panel.addComponent(new EmptySpace());
                panel.addComponent(new Label("Also coming up").addStyle(SGR.BOLD));
                for (int i = 1; i < Math.min(5, ranked.size()); i++) {
                    panel.addComponent(new Label("  " + formatTask(ranked.get(i).task())));
                }
            }

            // Main action. Both buttons ask the Controller; the View never changes the Model.
            if (task.getStatus() == Status.TODO) {
                buttons.addComponent(new Button("Start it", () -> {
                    pendingSelectId = task.getId(); // the board cursor will follow the task
                    listener.onMoveTask(task.getId(), Status.IN_PROGRESS);
                    todayWindow.close();
                }));
            } else {
                buttons.addComponent(new Button("Mark as done", () -> {
                    pendingSelectId = task.getId();
                    listener.onMoveTask(task.getId(), Status.DONE);
                    todayWindow.close();
                }));
            }
        }
        buttons.addComponent(new Button("Open board", todayWindow::close));

        panel.addComponent(new EmptySpace());
        panel.addComponent(buttons);
        panel.addComponent(new Label("Tab: next button | Enter: choose | Esc: go to the board"));
        todayWindow.setComponent(panel);

        // Esc = go to the board (same behaviour as in the task form).
        todayWindow.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    todayWindow.close();
                    hasBeenHandled.set(true);
                }
            }
        });

        gui.addWindowAndWait(todayWindow); // blocks until the window is closed
    }

    // ------------------------------------------------------------------ Subjects screen

    @Override
    public void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts) {
        this.shownSubjects = new ArrayList<>(subjects); // copy: the Model's list may change
        this.subjectTaskCounts = taskCounts;

        // Already open (the Controller refreshes it after every change): just refill the list.
        if (subjectsList != null) {
            fillSubjectsList();
            return;
        }

        subjectsList = new ActionListBox(new TerminalSize(46, 10));
        subjectsStatus = new Label(shownSubjects.isEmpty() ? "No subjects yet. Press N to add one." : " ");
        BasicWindow subjectsWindow = new BasicWindow("StudyBoard - Subjects");
        subjectsWindow.setHints(List.of(Window.Hint.CENTERED));
        fillSubjectsList();

        // Visible buttons (recognition) AND letter shortcuts (speed for experts).
        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));
        buttons.addComponent(new Button("New", this::newSubject));
        buttons.addComponent(new Button("Rename", this::renameSelectedSubject));
        buttons.addComponent(new Button("Delete", this::deleteSelectedSubject));
        buttons.addComponent(new Button("Close", subjectsWindow::close));

        Panel panel = new Panel();
        panel.addComponent(new Label("Your subjects (number of tasks on the right)"));
        panel.addComponent(subjectsList.withBorder(Borders.singleLine("Subjects")));
        panel.addComponent(buttons);
        panel.addComponent(new Label("N: new | R or Enter: rename | D: delete | Esc: back to the board"));
        panel.addComponent(subjectsStatus);
        subjectsWindow.setComponent(panel);

        subjectsWindow.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                if (keyStroke.getKeyType() != KeyType.Character
                        || keyStroke.isCtrlDown() || keyStroke.isAltDown()) {
                    return;
                }
                switch (Character.toLowerCase(keyStroke.getCharacter())) {
                    case 'n' -> { deliverEvent.set(false); newSubject(); }
                    case 'r' -> { deliverEvent.set(false); renameSelectedSubject(); }
                    case 'd' -> { deliverEvent.set(false); deleteSelectedSubject(); }
                    default -> { }
                }
            }

            @Override
            public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    subjectsWindow.close();
                    hasBeenHandled.set(true);
                }
            }
        });

        try {
            gui.addWindowAndWait(subjectsWindow); // blocks until the window closes
        } finally {
            subjectsList = null;   // "the screen is closed" for showSubjects() and showMessage()
            subjectsStatus = null;
        }
    }

    /** Refills the list from shownSubjects and keeps the cursor near where it was. */
    private void fillSubjectsList() {
        int previous = subjectsList.getSelectedIndex();
        subjectsList.clearItems();
        for (Subject subject : shownSubjects) {
            int count = subjectTaskCounts.getOrDefault(subject.getId(), 0);
            // Enter on an item = rename it.
            subjectsList.addItem(String.format("%-30s %3d task(s)", subject.getName(), count),
                    () -> renameSubject(subject));
        }
        if (!shownSubjects.isEmpty()) {
            subjectsList.setSelectedIndex(Math.max(0, Math.min(previous, shownSubjects.size() - 1)));
        }
    }

    private Subject selectedSubject() {
        int index = subjectsList.getSelectedIndex();
        return (index >= 0 && index < shownSubjects.size()) ? shownSubjects.get(index) : null;
    }

    private void newSubject() {
        subjectNameDialog("New subject", "", name -> {
            listener.onAddSubject(name);
            // The Controller already refreshed the list: put the cursor on the new (last) subject.
            subjectsList.setSelectedIndex(shownSubjects.size() - 1);
        });
    }

    private void renameSelectedSubject() {
        Subject subject = selectedSubject();
        if (subject == null) {
            showMessage("Select a subject first (arrow keys).");
            return;
        }
        renameSubject(subject);
    }

    private void renameSubject(Subject subject) {
        subjectNameDialog("Rename subject", subject.getName(),
                name -> listener.onRenameSubject(subject.getId(), name));
    }

    /**
     * Asks for a name and runs 'action' with it. If the name is not valid (the Model throws
     * IllegalArgumentException) the message is shown and the question is asked again with
     * the same text, so the user does not have to type everything again.
     */
    private void subjectNameDialog(String dialogTitle, String initialText, Consumer<String> action) {
        String text = initialText;
        while (true) {
            text = TextInputDialog.showDialog(gui, dialogTitle, "Subject name:", text);
            if (text == null) {           // Cancel or Esc
                showMessage("Cancelled.");
                return;
            }
            try {
                action.accept(text);
                return;                   // success
            } catch (IllegalArgumentException e) {
                MessageDialog.showMessageDialog(gui, "Please check the name", e.getMessage());
            }
        }
    }

    /** Confirmation (error prevention) that says exactly how many tasks will be lost. */
    private void deleteSelectedSubject() {
        Subject subject = selectedSubject();
        if (subject == null) {
            showMessage("Select a subject first (arrow keys).");
            return;
        }
        int count = subjectTaskCounts.getOrDefault(subject.getId(), 0);
        String text = (count == 0)
                ? "Delete the subject \"" + subject.getName() + "\"?"
                : "Delete the subject \"" + subject.getName() + "\" and its " + count
                        + " task(s)?\nThis cannot be undone.";
        // "No" first = the safe answer is the default one.
        MessageDialogButton answer = MessageDialog.showMessageDialog(gui, "Delete subject", text,
                MessageDialogButton.No, MessageDialogButton.Yes);
        if (answer == MessageDialogButton.Yes) {
            listener.onDeleteSubject(subject.getId());
        } else {
            showMessage("Delete cancelled.");
        }
    }
}