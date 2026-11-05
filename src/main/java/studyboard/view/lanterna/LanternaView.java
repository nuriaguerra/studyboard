package studyboard.view.lanterna;

import com.googlecode.lanterna.*;             // TerminalSize, SGR (bold text)
import com.googlecode.lanterna.gui2.*;        // Window, Panel, Label, Button, TextBox, ComboBox...
import com.googlecode.lanterna.gui2.dialogs.*; // MessageDialog, TextInputDialog, ActionListDialogBuilder
import com.googlecode.lanterna.input.*;       // KeyStroke, KeyType (the keys the user presses)
import com.googlecode.lanterna.screen.*;      // Screen, TerminalScreen
import com.googlecode.lanterna.terminal.*;    // Terminal, DefaultTerminalFactory
import java.io.*;                             // IOException
import java.time.*;                           // LocalDate, YearMonth
import java.time.format.*;                    // DateTimeFormatter, DateTimeParseException, ResolverStyle
import java.util.*;                           // List, ArrayList, Map, Locale, Comparator
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import studyboard.model.*;
import studyboard.view.*;

/**
 * LanternaView is the MVC View and it does two things:
 * it draws on the screen what the Controller tells it to draw,
 * and it calls the Controller when the user does something (the listener.onXxx(...) methods).
 *
 * The View never changes data.
 * It only calls listener.onXxx(...) (the Controller) and draws what the Controller sends with showXxx(...).
 */
public class LanternaView implements View {

    // Date formatters: 02.11 (short) and 02.11.2026 (long)

    // DateTimeFormatter.ofPattern creates a formatter that writes and reads dates with a specific pattern
    // (dd = day, MM = month, yyyy = year). It is static final because it never changes and is shared by all.
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // .withResolverStyle(ResolverStyle.STRICT) makes the formatter check the date strictly.
    // STRICT means that invalid dates (like 31.02.2026) are rejected instead of being silently
    // changed to a valid date (like 28.02.2026). "uuuu" is the year pattern that works with STRICT.
    private static final DateTimeFormatter DATE_INPUT = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    ///////Help texts (one per screen, so the help is "contextual")

    // A text between """ and """ is a "text block": a text of several lines. The spaces at the left are
    // cut automatically, so we can indent the text inside the code. It is only data: nothing runs here.
    private static final String BOARD_HELP = """
            MOVE AROUND
                Arrow keys / Tab   Choose a task or change column
                Enter              Show the details of a task

            TASKS
                N  New task           E  Edit the selected task
                D  Delete (asks first)
                M  Move task to the next column  (To do > In progress > Done)
                B  Move task to the previous column
                U  Undo the last change (up to 20)

            FIND
                /  Quick search       F  Filter and sort
                X  Clear the filter

            OTHER SCREENS
                T  Today: what to do next and why
                S  Subjects: create, rename, delete
                Q  Quit (your data is saved automatically)
                C  Calendar: deadlines by month """;

    // Help of the Today screen
    private static final String TODAY_HELP = """
            This screen suggests the next task to work on.
            "Why now?" lists the rules that gave it points:
            deadline, priority, size of the task and whether
            you have already started it.

                Tab     Next button
                Enter   Press the selected button
                Esc     Back to the board

            "Start it" / "Mark as done" moves the task for you.""";

    // Help of the Subjects screen
    private static final String SUBJECTS_HELP = """
                Up / Down   Choose a subject
                Enter       Show the menu for the selected subject

                N  New subject      R  Rename
                D  Delete (asks first and its tasks are deleted too)
                U  Undo the last change
            Esc         Back to the board""";

    // Help of the new / edit task form
    private static final String TASK_FORM_HELP = """
                Tab / Shift+Tab   Next / previous field
                Enter             Press the selected button
                Esc               Cancel without saving

            Deadline: DD.MM.YYYY (for example 25.11.2026).
            Estimate: hours, for example 2.5 (0 to 100).""";

    // Help of the search / filter / sort form
    private static final String FILTER_HELP = """
            Search text looks in title, notes and subject.
            All the conditions you set must match at the same time.
            "Clear filter" shows every task again.

                Tab   Next field        Esc   Cancel""";

    // Help of the calendar (it explains the symbols, so the user does not have to remember them)
    private static final String CALENDAR_HELP = """
                Left / Right   Previous / next month
                T              Back to the current month
                Esc            Back to the board

            [ 4]  = today
            *2    = 2 tasks due that day, at least one still pending
            +1    = 1 task due that day, already done""";

    // Indicates whether to use the emulated window (true, the --swing option) or the real terminal (false)
    private final boolean useSwing;

    // BasicWindow is a window that can contain components and be displayed on the screen.
    private final BasicWindow window = new BasicWindow("StudyBoard - Board"); // the main window of the application
    // ViewListener is the interface with the methods that the Controller implements to handle user actions.
    private ViewListener listener; // the Controller, it is assigned in setListener
    // MultiWindowTextGUI is the class that manages several windows and sends the keys to the top one.
    private MultiWindowTextGUI gui;  // window manager (it is created in run(), so it is null before that)

    ////// State of the board screen: the lists of tasks of each column and other data we must remember

    // ActionListBox is a component that shows a list of items; each item has a text and an action (run on Enter).
    private final ActionListBox[] lists = new ActionListBox[Status.values().length]; // one list per column (To do, In progress, Done)
    // A ActionListBox only stores texts and actions, not Task objects, so we keep the tasks of each column in
    // a parallel list (same order). columnTasks.get(column).get(row) = the task shown in that row.
    private final List<List<Task>> columnTasks = new ArrayList<>();
    private Label statusLine = new Label(" "); // label for the messages to the user (it starts empty)
    private Map<Integer, String> subjectNames = Map.of(); // subjectId -> subject name (Map.of() = empty and unmodifiable)
    private LocalDate today = LocalDate.now(); // today's date (to know if a task is late and to write "due in N days")
    private Integer pendingSelectId; // id of the task to select again after redrawing (null = none). Integer can be null.
    
    // The current board options: active filter, sort order and total number of tasks.
    // The first value is only a starting point; the Controller sends the real one on every redraw.
    private BoardOptions options = new BoardOptions(TaskFilter.NONE, SortOrder.DEADLINE, 0);

    ///// State of the Subjects screen (subjectsList is null while that screen is closed)
    private ActionListBox subjectsList; // the list of subjects on the screen
    private Label subjectsStatus;   // label for messages inside the Subjects screen
    private List<Subject> shownSubjects = new ArrayList<>(); // subjects currently shown in the list (same order as the rows)
    // Map of subjectId -> number of tasks of that subject
    private Map<Integer, Integer> subjectTaskCounts = Map.of();

    private YearMonth calMonth; // month shown by the calendar (YearMonth = a year and a month, without a day)

    // constructor

    public LanternaView(boolean useSwing) {
        this.useSwing = useSwing; // store the preference for the emulated window
        window.setHints(List.of(Window.Hint.FULL_SCREEN)); // the window will occupy the whole terminal

        // onInput is called before the component sees the key, so we only react to our own letters and leave arrows / Enter / Tab alone.
        // "new WindowListenerAdapter() { ... }" creates an object of an anonymous class: a class without
        // a name that we only need once. The Adapter has all its methods empty, so we only override onInput.
        window.addWindowListener(new WindowListenerAdapter() {

            @Override // onInput is called every time a key is pressed while this window is on top
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                // If the key is not a character key, or Ctrl or Alt is pressed, we ignore it and leave the method.
                //Arrows, Enter, Tab, Esc... are not "Character" keys, so they keep working normally.
                if (key.getKeyType() != KeyType.Character || key.isCtrlDown() || key.isAltDown()) {
                    return;
                }
                // deliverEvent.set(false) for keep the key for ourselves (the window does not send it to the components).
                //AtomicBoolean holds a boolean: the method changes the content of the box
                // and Lanterna reads it afterwards (a plain boolean parameter could not be changed from here).

                switch (Character.toLowerCase(key.getCharacter())) { // lowercase, so N and n do the same

                    // "case X -> { ... }" runs only that block (no "break" is needed, it never falls into the next case)
                    case 'q' -> { deliverEvent.set(false); window.close(); }   // quit: closing the main window ends run()
                    case 'm' -> { deliverEvent.set(false); moveSelectedTask(+1); }  // move right
                    case 'b' -> { deliverEvent.set(false); moveSelectedTask(-1); }  // move left (b = back)
                    case 'n' -> { deliverEvent.set(false); showTaskForm(null); } // new task (null when there is no existing task)
                    case 'e' -> { deliverEvent.set(false); editSelectedTask(); }  // edit
                    case 'd' -> { deliverEvent.set(false); deleteSelectedTask(); } // delete
                    case 't' -> { deliverEvent.set(false); listener.onShowToday(); } // today (we ask the Controller)
                    case 's' -> { deliverEvent.set(false); listener.onShowSubjects(); } // subjects (we ask the Controller)
                    case 'f' -> { deliverEvent.set(false); showFilterForm(); }  // filter and sort
                    case '/' -> { deliverEvent.set(false); quickSearch(); }    // quick search
                    case 'x' -> { deliverEvent.set(false); clearFilter(); }   // default filter (show all tasks)
                    case 'u' -> { deliverEvent.set(false); listener.onUndo(); } // undo
                    case 'c' -> { deliverEvent.set(false); listener.onShowCalendar(); } // calendar
                    default -> { }    // do nothing for any other key
                }
            }
        });
        // Help is configured separately because it is a reusable global feature.
        // Instead of duplicating F1/?/H code in every screen, 'helpOnKey' registers a 
        // second listener dedicated exclusively to displaying the help dialog.
        helpOnKey(window, "Help - Board", BOARD_HELP, true); // F1, H or ? shows the help of the board screen
    }

    @Override // View interface method: the Controller calls it from its own constructor
    public void setListener(ViewListener listener) {
        this.listener = listener; // save the listener (the Controller) so we can call it when the user does something
    }

    /////// board

    @Override // View interface method. The Controller calls it every time the board must be redrawn.
    public void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today, BoardOptions options) {
        this.subjectNames = subjectNames; // save the subject names, because formatTask() and other need them
        this.today = today; //save the current date for deadlines and for the text on the screen
        this.options = options; //save the filter and sort

        Status[] statuses = Status.values(); // [todo, in_progress, done]: one column for each one
        columnTasks.clear(); // forget the previous columns (we rebuild the whole screen every time, so we do not need to keep the old data)

        //A panel with 3 columns (one for each status), one next to the other.
        //GridLayout places the components in a grid with the given number of columns.
        Panel board = new Panel(new GridLayout(statuses.length)); //statuses.length = 3, so the grid has 3 columns and as many rows as needed
        for (int i = 0; i < statuses.length; i++) {
            List<Task> inColumn = new ArrayList<>(); // tasks of the current column, added in the same order as the rows of the ActionListBox
            ActionListBox list = new ActionListBox(new TerminalSize(28, 12)); // list to choose one task (28 characters wide, 12 lines high)
            for (Task task : tasks) {
                if (task.getStatus() == statuses[i]) { //if the task belongs to this column, we add it to the list
                    inColumn.add(task); //remember the task, in the same order as its row in the list

                    //The lambda () -> showDetails(task) is code that runs later, when the user presses Enter on this row.
                    // It can use "task" because the variable of the for loop is never reassigned.
                    list.addItem(formatTask(task), () -> showDetails(task)); // row text + action to run on Enter
                }
            }
            columnTasks.add(inColumn); //the list of this column goes at position i
            lists[i] = list; //save the ActionListBox of this column (selectedTask() compares against them)

            // The column is wrapped in a Border with the status label and the number of tasks (example: "To do (5)").
            Border column = list.withBorder(Borders.singleLine(statuses[i].getLabel() + " (" + inColumn.size() + ")"));
            // FILL + true,true = the column grows to use all the free space (horizontal and vertical)
            column.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, true));
            board.addComponent(column); //the column goes into the grid of the board
        }

        // Whole content of the screen, from top to bottom (the default layout of a Panel is vertical)
        Panel root = new Panel(); //Panel is a container that can hold several components, one below the other by default

        // The first line shows the date, the sort order and how many tasks are shown out of the total.
        root.addComponent(new Label("Today: " + today.format(LONG_DATE)
                + "   |   Sort: " + options.sort().getLabel()
                + "   |   Showing " + tasks.size() + " of " + options.totalTasks() + " tasks"));
        
        // The active filter is always visible, written in words and in bold (the user never forgets it is on)
        if (options.filter().isActive()) {
            root.addComponent(new Label("FILTER ON: " + options.filter().describe(subjectNames) + "   (X: clear)").addStyle(SGR.BOLD));
        }
        // A filter that hides everything would look like an error, so we explain it
        if (tasks.isEmpty() && options.totalTasks() > 0) { //options.totalTasks() > 0 means that there are tasks, but the filter hides them all
            root.addComponent(new Label("No task matches the current filter. Press F to change it or X to clear it."));
        }
        // The board is placed in a vertical layout that allows it to grow and take up the available space
        board.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow)); // the board takes the free space
        root.addComponent(board); // the board goes below the date and filter lines

        //short instructions for the user, always visible at the bottom of the screen
        root.addComponent(new Label("Enter: details | N: new | E: edit | D: delete | M/B: move task | U: undo"));
        root.addComponent(new Label("/: search | F: filter and sort | X: clear filter | T: Today | S: subjects"));
        root.addComponent(new Label("C: calendar | H or F1: help | Q: quit"));
        statusLine = new Label(" "); // empty line where the messages will appear
        root.addComponent(statusLine);
        window.setComponent(root); // we put everything in the window (it replaces the previous content)

        restoreSelection(); // after redrawing, we put the cursor on the task that was just moved or edited
    }

    @Override // View interface method: shows a short feedback message at the bottom of the screen
    public void showMessage(String message) {
        statusLine.setText(message); //the message is shown at the bottom of the board screen
        if (subjectsStatus != null) { // if the Subjects screen is open, we show it there too
            subjectsStatus.setText(message);
        }
    }

    @Override // View interface method: starts Lanterna and blocks until the user quits
    public void run() {
        Screen screen = null; // declared outside the try block so "finally" can also see it
        try {
            // Real terminal by default but "--swing" opens a separate emulator window when the user runs "java -jar StudyBoard.jar --swing".
            // The factory decides which kind of terminal is created.
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setForceTextTerminal(!useSwing); // without --swing: force the real text terminal
            factory.setPreferTerminalEmulator(useSwing); // with --swing: prefer the emulated window
            Terminal terminal = factory.createTerminal();

            screen = new TerminalScreen(terminal); // the Screen is a virtual screen: Lanterna draws on it and copies only the changes
            screen.startScreen(); // activates the text mode
            gui = new MultiWindowTextGUI(screen);  // window manager
            gui.addWindowAndWait(window); // shows the board and waits until it is closed (Q)
        } catch (IOException e) {
            // IOException is a "checked" exception, so we wrap it in an unchecked one.
            throw new UncheckedIOException("Cannot start the text user interface", e);
        } finally {
            // finally runs always, so the terminal goes back to normal even if there was an error
            if (screen != null) {
                try {
                    screen.stopScreen(); // returns the terminal to normal mode
                } catch (IOException ignored) { } // we ignore it because there is nothing we can do if the terminal does not return to normal
            }
        }
    }

    ///// general helpers

    /** One line per task. Priority symbol + "LATE" if the task is overdue. */
    private String formatTask(Task task) {
        // getOrDefault looks for the key in the map and returns "?" if the subject does not exist
        String subject = subjectNames.getOrDefault(task.getSubjectId(), "?");
        String late = task.isOverdue(today) ? "LATE " : ""; // condition ? valueIfTrue : valueIfFalse

        // %-2s = a text left-aligned in 2 characters, %s = a text. So "!!", "! " and "  " keep the titles aligned.
        return String.format("%-2s %s%s (%s, %s)", task.getPriority().getSymbol(), late, task.getTitle(), subject, task.getDeadline().format(SHORT_DATE));
    }

    /** Makes the Esc key close the given window (ALL the secondary windows use it). */
    private void closeOnEsc(BasicWindow w) {
        w.addWindowListener(new WindowListenerAdapter() { //add a listener to the window that reacts to keys

            //onUnhandledInput is called when the window does not know what to do with a key (it is "unhandled").
            // (so Esc is not used by any component inside the window, and we can use it for closing the window)
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                if (key.getKeyType() == KeyType.Escape) { //if the key is Esc, we close the window and say that we handled it (so the window does not send it to the components)
                    w.close();
                    handled.set(true); //this key is already used, its an atomic boolean so we can change it from here and Lanterna will read it afterwards
                }
            }
        });
    }

    /**
     * Opens the help of a window with F1. If letterKeys is true, also with H or ?.
     * We use F1 and letters because some terminals keep the F1 key for their own help.
     */
    private void helpOnKey(BasicWindow w, String title, String text, boolean letterKeys) {
        w.addWindowListener(new WindowListenerAdapter() { //add a listener to the window that reacts to keys
            @Override // onInput is called every time a key is pressed while this window is on top
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {

                boolean f1 = key.getKeyType() == KeyType.F1; // the F1 key is not a "Character" key
                // A letter counts only if letterKeys is true, it is a normal character (no Ctrl / Alt) and it is h, H or ?
                boolean letter = letterKeys && key.getKeyType() == KeyType.Character
                        && !key.isCtrlDown() && !key.isAltDown() && (key.getCharacter() == 'h' || key.getCharacter() == 'H' || key.getCharacter() == '?');
                if (f1 || letter) { // if the user pressed F1 or H or ?, we show the help dialog and keep the key for ourselves
                    deliverEvent.set(false); // the key is ours
                    MessageDialog.showMessageDialog(gui, title, text); // show the help dialog with the given title and text
                }
            }
        });
    }

    /** Yes/No question (error prevention). "No" goes first: the safe answer is the initial one. */
    private boolean confirm(String title, String text) {
        // The first button of the list is the one that has the focus when the dialog opens, so we put "No" first to avoid accidental confirmation.
        MessageDialogButton answer = MessageDialog.showMessageDialog(gui, title, text, MessageDialogButton.No, MessageDialogButton.Yes);
        //messageDialog.No and messageDialog.Yes are the two buttons of the dialog. The method returns the button that the user pressed.

        return answer == MessageDialogButton.Yes; // true only if the user explicitly chose Yes
    }

    /** Dialog with all the information of a task (Enter key). */
    private void showDetails(Task task) {
        long days = task.daysUntilDeadline(today); // long, because daysUntilDeadline returns a long

        //condition ? ifTrue : ifFalse (the second one is inside the "ifFalse" part)
        String when = days < 0 ? Math.abs(days) + " day(s) overdue" : days == 0 ? "due today" : "due in " + days + " day(s)";
        // We build the text piece by piece with +. "\n" is a line break.
        String text = "Title:     " + task.getTitle()
                + "\nSubject:   " + subjectNames.getOrDefault(task.getSubjectId(), "?")
                + "\nStatus:    " + task.getStatus().getLabel()
                + "\nPriority:  " + task.getPriority().getLabel()
                + "\nDeadline:  " + task.getDeadline().format(LONG_DATE) + " (" + when + ")"
                + "\nEstimate:  " + task.getEstimatedHours() + " h"
                + "\nNotes:     " + (task.getNotes().isEmpty() ? "-" : task.getNotes());
        MessageDialog.showMessageDialog(gui, "Task details", text);
    }

    ////// selection and movement

    /** Task highlighted in the column that has the focus, or null if there is none. */
    private Task selectedTask() {
        Interactable focused = window.getFocusedInteractable(); //getFocusedInteractable() returns an Interactable, which is the interface that ActionListBox implements
        for (int i = 0; i < lists.length; i++) {
            if (focused == lists[i]) { //if the focused component is the list of this column
                int index = lists[i].getSelectedIndex();  // the row that is highlighted in that column
                if (index >= 0 && index < columnTasks.get(i).size()) { // the index must be inside the list
                    return columnTasks.get(i).get(index); // return the task of that row in that column
                }
            }
        }
        return null; //the focus is not on a column, or the column is empty
    }

    /** Returns the selected task; if there is none, it warns the user and returns null. */
    private Task selectedTaskOrWarn() {
        Task task = selectedTask(); // the task highlighted in the column that has the focus
        if (task == null) {
            showMessage("Select a task first (arrow keys).");
        }
        return task;
    }

    /** direction +1 = next column, -1 = previous column. It asks the Controller to do the change. */
    private void moveSelectedTask(int direction) {
        Task task = selectedTaskOrWarn(); //the task highlighted in the column that has the focus
        if (task == null) return;
        int target = task.getStatus().ordinal() + direction; // ordinal = position of the enum value (0, 1, 2), direction = +1 or -1, so target = the column where we want to move the task
        Status[] all = Status.values(); //all the possible statuses (To do, In progress, Done) in the order of their ordinal
        if (target < 0 || target >= all.length) { // there is no column before the first or after the last
            showMessage("The task is already in the " + (direction > 0 ? "last" : "first") + " column.");
            return;
        }
        //first we save the id, then we call the Controller. The Controller redraws inside that call, and restoreSelection() needs to know the id already.
        pendingSelectId = task.getId(); // the cursor will follow the task
        listener.onMoveTask(task.getId(), all[target]);  // the Controller changes the Model and redraws
    }

    /** After redrawing, puts the cursor on the task that was just moved or edited. */
    private void restoreSelection() {
        if (pendingSelectId == null) return; // nothing to restore
        for (int c = 0; c < columnTasks.size(); c++) { // for each column
            for (int i = 0; i < columnTasks.get(c).size(); i++) { // for each task in that column
                if (columnTasks.get(c).get(i).getId() == pendingSelectId) { // we found the task id that was just moved or edited
                    window.setFocusedInteractable(lists[c]); // we give the focus to that column
                    lists[c].setSelectedIndex(i); // we highlight the row of that task
                }
            }
        }
        pendingSelectId = null; // done so we forget it
    }

    //////  create / edit / delete

    private void editSelectedTask() {
        Task task = selectedTaskOrWarn();
        if (task != null) showTaskForm(task); //when the task is selected, we open the form with that task (the form knows it is editing because the parameter is not null)
    }

    private void deleteSelectedTask() {
        Task task = selectedTaskOrWarn();
        if (task == null) return;
        // \" writes a quote character inside a String
        if (confirm("Delete task", "Delete \"" + task.getTitle() + "\"?")) {
            listener.onDeleteTask(task.getId()); // the Controller deletes the task and redraws the board
        } else {
            showMessage("Delete cancelled.");
        }
    }

    //////  Form. existing == null means "new task", otherwise "edit this task".
    private void showTaskForm(Task existing) {
        if (subjectNames.isEmpty()) { // a task needs a subject, so without subjects there is nothing to choose
            showMessage("There are no subjects yet. Press S to create one.");
            return;
        }
        boolean editing = existing != null; // if we are editing, the existing task is not null
        BasicWindow form = new BasicWindow(editing ? "Edit task" : "New task"); // the title of the window depends on whether we are editing or creating a new task
        form.setHints(List.of(Window.Hint.CENTERED)); // centered window

        // The two lists have the same order (the map keeps the insertion order), so the position that the user
        // chooses in the drop-down list can be translated into a subject id with subjectIds.get(position)
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet()); //list of the subject ids
        List<String> subjectLabels = new ArrayList<>(subjectNames.values()); //list of the subject names
        List<String> priorityLabels = new ArrayList<>(); //list of the priority labels (the enum values are not Strings, so we build a list of their labels)
        for (Priority p : Priority.values()) {
            priorityLabels.add(p.getLabel()); // the label of each priority (low, medium, high)
        }

        // Input fields, already filled when we are editing.
        // TerminalSize(40, 1) = 40 characters wide and 1 line high.
        TextBox title = new TextBox(new TerminalSize(40, 1), editing ? existing.getTitle() : ""); //if we are editing, we fill the field with the existing title, otherwise it is empty
        ComboBox<String> subject = new ComboBox<String>(subjectLabels);   // drop-down list subjects. The user can choose one of the subjects, and we will translate it into the subject id with subjectIds.get(selectedIndex)
        ComboBox<String> priority = new ComboBox<String>(priorityLabels); // drop-down list priorities
        LocalDate defaultDate = editing ? existing.getDeadline() : today.plusDays(7); // new tasks start with "in a week"

        TextBox date = new TextBox(new TerminalSize(14, 1), defaultDate.format(LONG_DATE));
        TextBox hours = new TextBox(new TerminalSize(8, 1), editing ? String.valueOf(existing.getEstimatedHours()) : "1.0");
        TextBox notes = new TextBox(new TerminalSize(40, 1), editing ? existing.getNotes() : "");

        // If we are editing an existing task, we preselect the values in the input fields.
        if (editing) { // we preselect the values of the task
            int subjectIndex = subjectIds.indexOf(existing.getSubjectId()); // position of the id in the list (-1 if missing)
            if (subjectIndex >= 0) { // the subject still exists
                subject.setSelectedIndex(subjectIndex); // preselect the subject in the drop-down list
            }
            priority.setSelectedIndex(existing.getPriority().ordinal());
        } else {
            priority.setSelectedIndex(Priority.MEDIUM.ordinal()); // default: medium
        }

        // The Save button receives a lambda: the code between { } runs when the button is pressed, not now.
        // The lambda can use title, subject, etc. because those variables are never reassigned.
        Button save = new Button("Save", () -> {
            try {
                //Creating TaskData validates everything: if something is wrong it throws IllegalArgumentException
                TaskData data = new TaskData(title.getText(), notes.getText(), subjectIds.get(subject.getSelectedIndex()),
                        Priority.values()[priority.getSelectedIndex()], parseDate(date.getText()), parseHours(hours.getText()));
                if (editing) {
                    pendingSelectId = existing.getId(); // the cursor will follow the task after redrawing
                    listener.onEditTask(existing.getId(), data); // the Controller updates the task and redraws the board
                } else {
                    listener.onAddTask(data); // the Controller adds the task and redraws the board
                }
                form.close();
            } catch (IllegalArgumentException e) {
                // The form stays open, so the user can fix the problem without typing everything again
                MessageDialog.showMessageDialog(gui, "Please check the form", e.getMessage());
            }
        });
        Button cancel = new Button("Cancel", form::close); // form::close is a method reference, equivalent to () -> form.close()

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL)); // buttons in a row
        buttons.addComponent(save);
        buttons.addComponent(cancel);
        // The last two numbers (2, 1) mean: this panel uses 2 columns and 1 row of the grid
        buttons.setLayoutData(GridLayout.createLayoutData(
            GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false, 2, 1));
        // The grid layout of the form has 2 columns: the first one for the labels, the second one for the input fields.

        Panel panel = new Panel(new GridLayout(2)); // grid of 2 columns: label | field
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
        panel.addComponent(new EmptySpace()); // empty cell, so the buttons go to the second column
        panel.addComponent(buttons);
        form.setComponent(panel);

        closeOnEsc(form); // Esc cancels
        helpOnKey(form, "Help - Task form", TASK_FORM_HELP, false); // F1, H or ? shows the help of the form
        gui.addWindowAndWait(form); //it stays here until the form is closed
    }

    /** Text -> date, with a message that says exactly what is wrong. */
    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_INPUT); // STRICT formatter: 31.02.2026 fails
        } catch (DateTimeParseException e) {
            // We throw our own exception with our message
            throw new IllegalArgumentException("The deadline must be a real date written as DD.MM.YYYY, for example " + today.plusDays(7).format(LONG_DATE) + ".");
        }
    }

    /** Text -> hours. It accepts "2.5" and "2,5". */
    private double parseHours(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.')); // a comma is changed to a dot first
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The estimate must be a number of hours, for example 2.5.");
        }
    }

    ///////Today screen

    @Override // View interface method. 'ranked' is already sorted by the Controller: the first one is the best suggestion.
    public void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today) {
        this.subjectNames = subjectNames; // formatTask() uses these two fields
        this.today = today;

        BasicWindow todayWindow = new BasicWindow("StudyBoard - Today");
        todayWindow.setHints(List.of(Window.Hint.CENTERED));

        Panel panel = new Panel();
        panel.addComponent(new Label("Today: " + today.format(LONG_DATE))); // the date is always visible at the top
        panel.addComponent(new EmptySpace());

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL)); // created before the if, "Open board" is always added

        if (ranked.isEmpty()) { //ranked is for today, so if it is empty there is nothing to do today
            panel.addComponent(new Label("Nothing pending. All your tasks are done!"));
        } else {
            Recommendation next = ranked.get(0); // the best suggestion
            Task task = next.task(); // the task of that suggestion

            // The suggestion, in a box with a border
            Panel card = new Panel();
            card.addComponent(new Label(task.getTitle()).addStyle(SGR.BOLD));
            card.addComponent(new Label(subjectNames.getOrDefault(task.getSubjectId(), "?")
                    + " | due " + task.getDeadline().format(LONG_DATE)
                    + " | " + task.getEstimatedHours() + " h"
                    + " | " + task.getPriority().getLabel() + " priority"));
            card.addComponent(new EmptySpace());
            card.addComponent(new Label("Why now?").addStyle(SGR.BOLD));
            for (String reason : next.reasons()) { // one line for each reason
                card.addComponent(new Label("  - " + reason));
            }
            card.addComponent(new Label("Score: " + next.score()));
            panel.addComponent(card.withBorder(Borders.singleLine("Next up")));

            // The next 4 suggestions, one line each (Math.min stops at the end of the list if there are fewer)
            if (ranked.size() > 1) {
                panel.addComponent(new EmptySpace());
                panel.addComponent(new Label("Also coming up").addStyle(SGR.BOLD));
                for (int i = 1; i < Math.min(5, ranked.size()); i++) { //Math.min(5, ranked.size()) = the smaller of 5 and the number of suggestions, so we do not go out of bounds
                    panel.addComponent(new Label("  " + formatTask(ranked.get(i).task())));
                }
            }

            // Both buttons ask the Controller; the View does not change the Model.
            if (task.getStatus() == Status.TODO) {
                buttons.addComponent(new Button("Start it", () -> { 
                    pendingSelectId = task.getId(); // the cursor of the board will follow the task
                    listener.onMoveTask(task.getId(), Status.IN_PROGRESS); // the Controller changes the Model and redraws the board
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
        todayWindow.setComponent(panel); //the panel goes into the window

        closeOnEsc(todayWindow); // Esc = back to the board
        helpOnKey(todayWindow, "Help - Today", TODAY_HELP, true);
        gui.addWindowAndWait(todayWindow); // it waits until the window is closed
    }

    ///////Subjects screen

    // The Controller calls showSubjects both to open the screen and to refresh it after every change.
    //The first call opens the window and blocks inside addWindowAndWait. While it is blocked, an action
    // (example: "new subject") makes the Controller call showSubjects again: in that second call
    // subjectsList is not null, so we only refill the list and return. The first call is still waiting.
    @Override
    public void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts) {
        this.shownSubjects = new ArrayList<>(subjects); // a copy, so the Controller can change the original list without affecting the View
        this.subjectTaskCounts = taskCounts; // a copy, so the Controller can change the original map without affecting the View

        // It is already open (the Controller refreshes it after every change)
        if (subjectsList != null) {
            fillSubjectsList();
            return;
        }

        subjectsList = new ActionListBox(new TerminalSize(46, 10)); // list of subjects, 46 characters wide and 10 lines high
        subjectsStatus = new Label(shownSubjects.isEmpty() ? "No subjects yet. Press N to add one." : " ");
        BasicWindow subjectsWindow = new BasicWindow("StudyBoard - Subjects");
        subjectsWindow.setHints(List.of(Window.Hint.CENTERED));
        fillSubjectsList(); // fill the list with the subjects that were passed by the Controller

        // No buttons: this way the focus never leaves the list and the highlighted subject is always the right one.
        //(With buttons, going down to them made the selection pass through the last subject.)
        Panel panel = new Panel();
        panel.addComponent(new Label("Your subjects (number of tasks on the right)"));
        panel.addComponent(subjectsList.withBorder(Borders.singleLine("Subjects")));
        panel.addComponent(new Label("Enter: menu | N: new | R: rename | D: delete | U: undo | H: help | Esc: back"));
        panel.addComponent(subjectsStatus);
        subjectsWindow.setComponent(panel);

        closeOnEsc(subjectsWindow); //Esc closes
        helpOnKey(subjectsWindow, "Help - Subjects", SUBJECTS_HELP, true);

        // The window listens to keys that are not used by the list (Enter, arrow keys, etc. are used by the list)
        subjectsWindow.addWindowListener(new WindowListenerAdapter() {
            @Override // onInput is called every time a key is pressed while this window is on top
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                if (key.getKeyType() != KeyType.Character || key.isCtrlDown() || key.isAltDown()) {
                    return;
                }
                switch (Character.toLowerCase(key.getCharacter())) {
                    case 'n' -> { deliverEvent.set(false); newSubject(); }
                    case 'r' -> { deliverEvent.set(false); renameSelectedSubject(); }
                    case 'd' -> { deliverEvent.set(false); deleteSelectedSubject(); }
                    // Undo and refresh the list (the screen stays open, so the list is only refilled)
                    case 'u' -> { deliverEvent.set(false); listener.onUndo(); listener.onShowSubjects(); }
                    default -> { }
                }
            }
        });

        try {
            gui.addWindowAndWait(subjectsWindow); //it waits until the window is closed
        } finally {
            subjectsList = null;   // "the screen is closed" for showSubjects() and showMessage()
            subjectsStatus = null;
        }
    }

    /** Refills the list from shownSubjects and leaves the cursor near where it was. */
    private void fillSubjectsList() {
        int previous = subjectsList.getSelectedIndex(); // remember where the cursor was
        subjectsList.clearItems(); // remove all the items from the list, so we can refill it
        for (Subject subject : shownSubjects) { // for each subject, we add a row to the list
            int count = subjectTaskCounts.getOrDefault(subject.getId(), 0); // 0 if the subject has no tasks

            // %-30s = the name left-aligned in 30 characters, %3d = the number right-aligned in 3 characters
            subjectsList.addItem(String.format("%-30s %3d task(s)", subject.getName(), count),
                    () -> subjectMenu(subject)); // Enter = menu of actions
        }
        if (!shownSubjects.isEmpty()) {
            // Math.max / Math.min keep the index inside the list (for example after deleting the last subject)
            subjectsList.setSelectedIndex(Math.max(0, Math.min(previous, shownSubjects.size() - 1)));
        }
    }

    /** Highlighted subject, or null. */
    private Subject selectedSubject() {
        int index = subjectsList.getSelectedIndex();
        return (index >= 0 && index < shownSubjects.size()) ? shownSubjects.get(index) : null; //if the index is valid, return the subject at that index, otherwise return null
    }

    private void newSubject() {
        // The lambda (name -> { ... }) is the "action" that subjectNameDialog runs when the name is typed
        subjectNameDialog("New subject", "", name -> {
            listener.onAddSubject(name);
            // The Controller already refreshed the list, so we put the cursor on the new (last) subject
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

    /** Enter on a subject, shows a small menu with the possible actions. */
    private void subjectMenu(Subject subject) {
        // "Builder" pattern, every method returns the same builder, so the calls can be chained one after another
        new ActionListDialogBuilder()  // constructor creates a new builder, which is empty at first
                .setTitle(subject.getName()) // title = name of the subject
                .setDescription("Choose an action") // description = text below the title
                .addAction("Rename", () -> renameSubject(subject)) // every option runs its own lambda
                .addAction("Delete", () -> deleteSubject(subject)) // with that subject
                .build() // creates the dialog from the builder
                .showDialog(gui);// it is shown and it waits
    }

    /** Asks for a new name and sends it to the Controller. */
    private void renameSubject(Subject subject) {
        subjectNameDialog("Rename subject", subject.getName(), name -> listener.onRenameSubject(subject.getId(), name));
        //.onReanmeSubject() redraws the list, so we do not have to do it here.
    }

    /**
     * Asks for a name and runs 'action' with it. If the name is not valid (the Model throws
     * IllegalArgumentException) the message is shown and the question is asked again with
     * the same text, so the user does not have to type everything again.
     */
    private void subjectNameDialog(String dialogTitle, String initialText, Consumer<String> action) {
        String text = initialText;
        while (true) { // repeats until it works or the user cancels (return leaves the loop)
            text = TextInputDialog.showDialog(gui, dialogTitle, "Subject name:", text); 
            // shows the dialog with the given title and text, and returns the text that the user typed (or null if the user pressed Esc or Cancel)
            if (text == null) {// Cancel or Esc
                showMessage("Cancelled.");
                return;
            }
            try {
                action.accept(text); //runs what we were given (create or rename)
                return; // success
            } catch (IllegalArgumentException e) {
                MessageDialog.showMessageDialog(gui, "Please check the name", e.getMessage());
            }
        }
    }

    /** D key: deletes the highlighted subject. */
    private void deleteSelectedSubject() {
        Subject subject = selectedSubject();
        if (subject == null) {
            showMessage("Select a subject first (arrow keys).");
            return;
        }
        deleteSubject(subject);
    }

    /** Asks for confirmation (saying how many tasks will be lost) and tells the Controller. */
    private void deleteSubject(Subject subject) {
        int count = subjectTaskCounts.getOrDefault(subject.getId(), 0); //how many tasks have that subject (0 if none)
        String text = (count == 0) // if no tasks, just ask for confirmation
                ? "Delete the subject \"" + subject.getName() + "\"?"
                : "Delete the subject \"" + subject.getName() + "\" and its " + count + " task(s)?\nThis cannot be undone.";
        if (confirm("Delete subject", text)) {
            listener.onDeleteSubject(subject.getId()); // the Controller deletes the subject and redraws the list
        } else {
            showMessage("Delete cancelled.");
        }
    }

    /////// search and filter

    /** "/" key, opens a small dialog. It keeps the other filter settings. */
    private void quickSearch() {
        // The current text is already in the filter, so we pass it to the dialog. If the user presses Esc or Cancel, we do not change anything.
        String text = TextInputDialog.showDialog(gui, "Search", "Look for (title, notes or subject). Empty = no search:", options.filter().text());
        if (text == null) { // Cancel or Esc
            showMessage("Search cancelled.");
            return;
        }
        // withText creates a new filter equal to the current one but with the new text
        listener.onChangeView(options.filter().withText(text), options.sort());
    }

    /** "X" key, removes the filter but keeps the sort order. */
    private void clearFilter() {
        if (!options.filter().isActive()) { // there is no filter to clear
            showMessage("No filter is active.");
            return;
        }
        listener.onChangeView(TaskFilter.NONE, options.sort()); // the Controller redraws the board with no filter
    }

    /** "F" key, opens the full form. It starts with the current values, so nothing has to be typed again. */
    private void showFilterForm() {
        TaskFilter current = options.filter();
        BasicWindow form = new BasicWindow("Search, filter and sort");
        form.setHints(List.of(Window.Hint.CENTERED));

        // Drop-down lists. Position 0 of subject / priority means "no restriction",
        // so the subjects and priorities start at position 1 (that is why there is a +1 / -1 below)
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> subjectLabels = new ArrayList<>();
        subjectLabels.add("All subjects"); // position 0 = no restriction
        subjectLabels.addAll(subjectNames.values()); // same order as subjectIds (shifted by 1)
        List<String> priorityLabels = new ArrayList<>();
        priorityLabels.add("Any priority"); // position 0 = no restriction
        for (Priority p : Priority.values()) { // the enum values are not Strings, so we build a list of their labels
            priorityLabels.add(p.getLabel());
        }
        List<String> sortLabels = new ArrayList<>();
        for (SortOrder s : SortOrder.values()) {
            sortLabels.add(s.getLabel());
        }

        TextBox text = new TextBox(new TerminalSize(30, 1), current.text());
        ComboBox<String> subject = new ComboBox<String>(subjectLabels);
        ComboBox<String> priority = new ComboBox<String>(priorityLabels);
        ComboBox<String> sort = new ComboBox<String>(sortLabels);
        CheckBox hideDone = new CheckBox("Hide them"); // checkbox
        hideDone.setChecked(current.hideDone());

        if (current.subjectId() != null) { // if there is a subject restriction, we preselect it in the drop-down list
            int index = subjectIds.indexOf(current.subjectId());
            if (index >= 0) subject.setSelectedIndex(index + 1); // +1 because of "All subjects"
        }
        if (current.priority() != null) { // if there is a priority restriction
            priority.setSelectedIndex(current.priority().ordinal() + 1);
        }
        sort.setSelectedIndex(options.sort().ordinal()); // preselect the current sort order

        Button apply = new Button("Apply", () -> {
            // Position 0 = "no restriction" = null (an Integer can be null, an int cannot)
            // if the user chooses "All subjects", we send null to the Controller, otherwise we translate the position into a subject id with subjectIds.get(position - 1)
            Integer subjectId = subject.getSelectedIndex() == 0 ? null : subjectIds.get(subject.getSelectedIndex() - 1);
            Priority chosenPriority = priority.getSelectedIndex() == 0 ? null : Priority.values()[priority.getSelectedIndex() - 1];
            // Apply the filter and sort order
            listener.onChangeView(new TaskFilter(text.getText(), subjectId, chosenPriority, hideDone.isChecked()),
                    SortOrder.values()[sort.getSelectedIndex()]); // the Controller redraws the board with the new filter and sort order
            form.close();
        });
        Button clear = new Button("Clear filter", () -> { // clears the filter but keeps the sort order
            listener.onChangeView(TaskFilter.NONE, SortOrder.values()[sort.getSelectedIndex()]);
            form.close();
        });
        Button cancel = new Button("Cancel", form::close);

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));
        buttons.addComponent(apply);
        buttons.addComponent(clear);
        buttons.addComponent(cancel);
        buttons.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false, 2, 1));

        Panel panel = new Panel(new GridLayout(2));
        panel.addComponent(new Label("Search text:"));
        panel.addComponent(text);
        panel.addComponent(new Label("Subject:"));
        panel.addComponent(subject);
        panel.addComponent(new Label("Priority:"));
        panel.addComponent(priority);
        panel.addComponent(new Label("Done tasks:"));
        panel.addComponent(hideDone);
        panel.addComponent(new Label("Sort by:"));
        panel.addComponent(sort);
        panel.addComponent(new Label("Tab: next field | Esc: cancel"));
        panel.addComponent(new EmptySpace());
        panel.addComponent(buttons);
        form.setComponent(panel);

        closeOnEsc(form);
        helpOnKey(form, "Help - Filter", FILTER_HELP, false); // only F1
        gui.addWindowAndWait(form);
    }

    ///////calendar

    @Override // View interface method. It receives all the tasks (the board filter does not hide deadlines).
    public void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today) {
        this.subjectNames = subjectNames; //formatTask() uses these two fields
        this.today = today;
        calMonth = YearMonth.from(today); //we start in the current month

        BasicWindow cal = new BasicWindow("StudyBoard - Calendar");
        cal.setHints(List.of(Window.Hint.CENTERED));
        drawMonth(cal, tasks); //draws the month inside the window

        closeOnEsc(cal);  // Esc goes back to the board
        helpOnKey(cal, "Help - Calendar", CALENDAR_HELP, true); // H, ? or F1
        // This window has no buttons or text boxes, so nothing uses the arrow keys and they reach onUnhandledInput

        cal.addWindowListener(new WindowListenerAdapter() { // the window listens to keys that are not used by any component (arrow keys, T, etc.)
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                if (key.getKeyType() == KeyType.ArrowLeft) {
                    calMonth = calMonth.minusMonths(1); // previous month
                } else if (key.getKeyType() == KeyType.ArrowRight) {
                    calMonth = calMonth.plusMonths(1); // next month
                } else if (key.getKeyType() == KeyType.Character && Character.toLowerCase(key.getCharacter()) == 't') {
                    calMonth = YearMonth.from(today);  // back to today's month
                } else {
                    return; // any other key no action for this window, so it is not handled and can be used by the board
                }
                drawMonth(cal, tasks); // we redraw with the new month
                handled.set(true); // the key is already used
            }
        });
        gui.addWindowAndWait(cal); //it waits until the window is closed
    }

    /** Builds the content of the window for the month stored in calMonth. It is called again on every month change. */
    private void drawMonth(BasicWindow cal, List<Task> tasks) {
        Panel root = new Panel();
        // Title: "November 2026" in bold. getDisplayName gives the name of the month in English.
        root.addComponent(new Label(calMonth.getMonth().getDisplayName( java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + calMonth.getYear())
                .addStyle(SGR.BOLD));

        Panel grid = new Panel(new GridLayout(7)); // grid of 7 columns = 7 days of the week
        for (String d : new String[] {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}) {
            grid.addComponent(new Label(String.format("%-6s", d))); // header, 6 characters wide
        }
        // Empty cells before day 1. getValue(): Monday = 1 ... Sunday = 7
        // (if the month starts on Wednesday, value = 3, so we add 2 empty cells: Monday and Tuesday)
        for (int i = 1; i < calMonth.atDay(1).getDayOfWeek().getValue(); i++) { //for each day of the week before the first day of the month, we add an empty cell
            grid.addComponent(new EmptySpace());
        }
        for (int day = 1; day <= calMonth.lengthOfMonth(); day++) { // for each day of the month, we add a cell with the number and the mark
            LocalDate date = calMonth.atDay(day); //the real date of this day
            int total = 0, pending = 0; // tasks of the day / tasks not finished
            for (Task t : tasks) {
                if (t.getDeadline().equals(date)) {
                    total++;
                    if (t.getStatus() != Status.DONE) pending++; // if the task is not done, it is pending
                }
            }
            String mark = total == 0 ? "  " : (pending > 0 ? "*" : "+") + total; // *2 = pending, +1 = all done
            boolean isToday = date.equals(today);
            // Today goes between brackets: [ 4]. %2d = the number in 2 characters. Every cell is 6 characters wide.
            grid.addComponent(new Label((isToday ? "[" : " ") + String.format("%2d", day) + (isToday ? "]" : " ") + mark + (mark.length() == 2 ? "" : " ")));
        }
        root.addComponent(grid);

        // List of the tasks of this month, sorted by date.
        //.stream() creates a stream of tasks, .filter() keeps only the tasks of this month, .sorted() sorts them by date, and .toList() collects them into a list.
        List<Task> inMonth = tasks.stream()
                .filter(t -> YearMonth.from(t.getDeadline()).equals(calMonth))
                .sorted(Comparator.comparing(Task::getDeadline)).toList();
        root.addComponent(new EmptySpace());
        root.addComponent(new Label(inMonth.isEmpty() ? "No deadlines this month." : "Deadlines this month:").addStyle(SGR.BOLD));
        for (int i = 0; i < Math.min(8, inMonth.size()); i++) { // maximum 8 lines
            Task t = inMonth.get(i);
            root.addComponent(new Label("  " + formatTask(t)    // we reuse the format of the board
                    + (t.getStatus() == Status.DONE ? " [done]" : "")));
        }
        if (inMonth.size() > 8) { // if there are more than 8 tasks, we show a line that says how many more there are
            root.addComponent(new Label("  ... and " + (inMonth.size() - 8) + " more"));
        }
        root.addComponent(new EmptySpace());
        root.addComponent(new Label("Left/Right: month | T: this month | H: help | Esc: back"));
        cal.setComponent(root); //replaces the previous content of the window (so the month is redrawn)
    }
}
