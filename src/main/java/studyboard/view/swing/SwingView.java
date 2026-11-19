package studyboard.view.swing;

import com.formdev.flatlaf.FlatDarkLaf;   // dark theme
import com.formdev.flatlaf.FlatLightLaf;  // light theme
import java.awt.*; // BorderLayout, GridLayout, Font, Dimension, Component, Window
import java.awt.datatransfer.*;  // StringSelection, Transferable, DataFlavor (used by drag and drop)
import java.awt.event.*; // MouseAdapter, MouseEvent
import java.time.*;  // LocalDate
import java.util.*;  // Map, ArrayList
import java.util.List;   // java.awt and java.util both have a "List", so we specify java.util's
import javax.swing.*;  // JFrame, JPanel, JList, JButton, JLabel...
import studyboard.model.*;
import studyboard.model.SortOrder; // javax.swing also has a SortOrder, this says we mean ours
import studyboard.view.*;

/**
 * view with a graphical window (Swing). It implements the same interface as LanternaView,
 * so the Controller does not know (and does not care) which one it is talking to.
 * 
 * the View does not change data. It only calls listener.onXxx(...) (asks the Controller) and draws what the Controller sends with showXxx(...).
 *
 * The secondary windows are in their own classes: TaskFormDialog, FilterDialog, TodayDialog...
 */
public class SwingView implements View {
    //implements the View interface, so the Controller can talk to it without knowing it is Swing


    private ViewListener listener; // the Controller

    private BoardOptions options = new BoardOptions(TaskFilter.NONE, SortOrder.DEADLINE, 0); // the last options the Controller sent to showBoard()
    private Map<Integer, String> subjectNames = Map.of(); // the last subject names the Controller sent, Id -> name
    private LocalDate today = LocalDate.now();
    private Integer pendingSelectId; //task to select again after redrawing (null = none), same idea as in text mode
    private boolean dark = false;  // is the dark theme on or off

    private final Status[] statuses = Status.values(); // one column per status: To do, In progress, Done
    // One entry per column, all in the same order as "statuses".

    private final List<DefaultListModel<Task>> models = new ArrayList<>(); //the data of each list
    private final List<JList<Task>> lists = new ArrayList<>(); // the visible lists
    private final List<JLabel> headers = new ArrayList<>();  // the titles "To do (3)"
    private final TaskCardRenderer renderer = new TaskCardRenderer(); //how a task looks as a card

    private final JFrame frame = new JFrame("StudyBoard"); //the main window of the program
    private final JLabel infoLabel = new JLabel(" ");  // "Today: ... | Sort: ... | Showing 3 of 8"
    private final JLabel filterLabel = new JLabel(" "); // "FILTER ON: ..." (only visible when a filter is active)
    private final JLabel statusBar = new JLabel(" "); // bottom line for messages
    private final JTextField searchField = new JTextField(18); // quick search
    private final JButton themeButton = new JButton("Dark mode");
    private SubjectsDialog subjectsDialog; // null while the Subjects window is closed

    //constructor: builds the window and its components, but does not show it yet. The Controller calls run() to show it.
    public SwingView() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // closing the window ends the program
        frame.setSize(1100, 700); //initial size of the window, but the user can resize it
        frame.setMinimumSize(new Dimension(800, 500)); // the window cannot be made smaller than this
        frame.setLocationRelativeTo(null); //null = centre on the screen

        //the three columns
        JPanel board = new JPanel(new GridLayout(1, statuses.length, 12, 0)); // 1 row, 3 columns: they share the width
        board.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12)); // empty space around the columns to avoid the cards touching the window edges

        for (int c = 0; c < statuses.length; c++) { // one column per status
            DefaultListModel<Task> model = new DefaultListModel<>(); //the data of this column (the tasks in it)
            
            JList<Task> list = new JList<>(model) { //create a list that shows the tasks of this column
                @Override protected void paintComponent(Graphics g) { // override the default paintComponent to show a hint when the column is empty
                    super.paintComponent(g);
                    if (getModel().getSize() == 0) {//if the row is empty, draw a hint in the middle of the column
                        g.setColor(Color.GRAY);
                        g.drawString("No tasks here", 14, 26); //14 pixels from the left, 26 from the top
                    }
                }
            };

            // The list uses Ctrl+Left or Right itself: remove them so the window shortcut works
            for (String k : new String[] {"ctrl LEFT", "ctrl RIGHT", "ctrl KP_LEFT", "ctrl KP_RIGHT"}) {
                list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(k), "none"); //when the list has focus, ignore these keys (the window will catch them)
                //if we dont do this the list will move the selection to the next/previous task instead of moving the task to the next/previous column
            }

            list.setCellRenderer(renderer); // every row is drawn as a card
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // only one task at a time
            // Selecting a task in this column clears the other columns, one selected task in the whole board

            //addListSelectionListener is called when the selection changes (user clicks on a card, or we call setSelectedIndex)
            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) { 
                    //getValueIsAdjusting is true while the user is dragging the mouse to select multiple rows, we only want to react when the selection is final
                    clearOtherSelections(list);
                }
            });
            // Double-click = details
            list.addMouseListener(new MouseAdapter() { //addMouseListener is called when the user clicks on a card, we use it to detect double-clicks
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                        showDetails(list.getSelectedValue());
                    }
                }
            });
            installDragAndDrop(list, c); // lets the user drag a card from this list and drop it on another column

            JLabel header = new JLabel(statuses[c].getLabel()); // the title of the column, e.g. "To do (3)"
            header.setFont(header.getFont().deriveFont(Font.BOLD, 14f)); // bold and slightly bigger than the default font
            JPanel column = new JPanel(new BorderLayout(0, 6)); // header on top, list below
            column.add(header, BorderLayout.NORTH);
            column.add(new JScrollPane(list), BorderLayout.CENTER); // adds scroll bars when needed
            board.add(column);

            //add to the lists so we can access them later
            models.add(model);
            lists.add(list);
            headers.add(header);
        }
        applyColumnColors(); // make the columns grey and the cards lighter

        //toolbar 1: what you can do with tasks
        JToolBar actions = new JToolBar();
        actions.setFloatable(false); // the user cannot drag the toolbar out of the window

        //add buttons to the toolbar, each button has a text, a tooltip and an action

        actions.add(button("New", "New task (Ctrl+N)", this::newTask));
        actions.add(button("Edit", "Edit the selected task (Ctrl+E)", this::editSelected));
        actions.add(button("Delete", "Delete the selected task (Delete)", this::deleteSelected));
        actions.addSeparator();
        actions.add(button("← Move left", "Move to the previous column (Ctrl+Left)", () -> moveSelected(-1))); //-1 = previous column
        actions.add(button("Move right →", "Move to the next column (Ctrl+Right)", () -> moveSelected(+1))); //+1 = next column
        actions.addSeparator();
        actions.add(button("Undo", "Undo the last change (Ctrl+Z)", () -> listener.onUndo()));
        actions.addSeparator();
        actions.add(button("Today", "What to do next (F2)", () -> listener.onShowToday()));
        actions.add(button("Subjects", "Create, rename, delete subjects (F3)", () -> listener.onShowSubjects()));
        actions.add(button("Calendar", "Deadlines by month (F4)", () -> listener.onShowCalendar()));

        //toolbar 2: how you see tasks

        //searchField were the user can type a text to filter the tasks by title, notes or subject
        searchField.setMaximumSize(searchField.getPreferredSize()); //have a fixed width, so the toolbar does not stretch it to fill the space

        //JTextField.placeholderText is a property of FlatLaf that shows a grey hint when the box is empty. It will be ignored if the user uses another theme.
        searchField.putClientProperty("JTextField.placeholderText", "Search title, notes, subject… (Enter)");
        searchField.setToolTipText("Search (Ctrl+F). Press Enter to apply."); //setToolTipText shows a text when the mouse rests on the box
        searchField.addActionListener(e ->   // Enter inside the box
                listener.onChangeView(options.filter().withText(searchField.getText()), options.sort())); // the Controller will call showBoard() with the new filter
        
        themeButton.setToolTipText("Switch between light and dark theme"); //setToolTipText shows a text when the mouse rests on the button
        themeButton.addActionListener(e -> toggleTheme()); //toogleTheme() switches between light and dark theme and repaints the window
        
        
        JToolBar view = new JToolBar();
        view.setFloatable(false); //the user cannot drag the toolbar out of the window
        view.add(searchField);
        view.add(button("Filter…", "Search, filter and sort", this::showFilter));
        view.add(button("Clear filter", "Show every task again", () -> listener.onChangeView(TaskFilter.NONE, options.sort())));
        view.add(Box.createHorizontalGlue()); //createHorizontalGlue() adds a flexible space that pushes the next buttons to the right
        view.add(button("Help", "Help of this window (F1)", () -> Ui.showText(frame, "Help - Board", HelpTexts.BOARD)));
        view.add(themeButton);

        //information line and filter line (only visible when a filter is active)
        infoLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 0, 12)); // empty space around the text so it does not touch the window edges
        filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD)); //the active filter is written in bold
        filterLabel.setBorder(BorderFactory.createEmptyBorder(2, 12, 0, 12));
        filterLabel.setVisible(false); // the filter line is only visible when a filter is active

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS)); //the toolbars and the info lines are stacked vertically, they share the width of the window
        for (JComponent c : new JComponent[] {actions, view, infoLabel, filterLabel}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT); //left-align for do not stretch the components to fill the width of the window
            top.add(c);
        }
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        //add the top panel (north), the board (center) and the status bar (south) to the window
        frame.add(top, BorderLayout.NORTH);
        frame.add(board, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);

        //key shortcuts for the whole window (not just the toolbars)
        JRootPane root = frame.getRootPane(); //root it receives key events even when the focus is on a text box or a button
        //bindkey(root, key, action) = when the user presses key, run action
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

        //helpOnF1(root, frame, title, text) = when the user presses F1, show a window with title and text
        Ui.helpOnF1(root, frame, "Help - Board", HelpTexts.BOARD);
    }

    /** Creates a toolbar button that runs 'action' when pressed. */
    private JButton button(String text, String tooltip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip); //text that appears when the mouse rests on the button
        b.addActionListener(e -> action.run());
        return b;
    }

    @Override
    public void setListener(ViewListener listener) {
        this.listener = listener; //the Controller registers itself here
    }

    
    ///////View interface

    @Override
    public void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today, BoardOptions options) { 
        //show the board with the given tasks, subject names, today and options

        this.options = options;
        this.subjectNames = subjectNames;
        this.today = today;
        renderer.update(subjectNames, today);  //cards (renderer) update with the new subject names and today, so they can show the subject name and know what is late

        for (DefaultListModel<Task> model : models) {
            model.clear(); //we empty the three lists and fill them again (like the text mode board)
        }
        for (Task task : tasks) {
            models.get(task.getStatus().ordinal()).addElement(task); //add the task of its status (column) ordinal() = 0, 1, 2
        }
        for (int i = 0; i < statuses.length; i++) { // update the column headers with the number of tasks in each column
            headers.get(i).setText(statuses[i].getLabel() + " (" + models.get(i).size() + ")"); //to do (3)
        }

        //update the info line and the filter line
        infoLabel.setText("Today: " + today.format(Ui.LONG_DATE) + "   |   Sort: " + options.sort().getLabel() //date, sort, number of tasks
                + "   |   Showing " + tasks.size() + " of " + options.totalTasks() + " tasks");

        filterLabel.setVisible(options.filter().isActive()); //the filter line is only visible when a filter is active
        filterLabel.setText("FILTER ON: " + options.filter().describe(subjectNames)); //the filter line shows the active filter ("FILTER ON: Subject = Math, Priority = High")
        searchField.setText(options.filter().text()); // the search box shows the text of the filter, so the user can edit it

        restoreSelection(); //after redrawing, select again the task that was moved or edited
        frame.revalidate(); //the window recalculates the size and position of its components
        frame.repaint(); //repaint the window, so the changes are visible immediately
    }

    @Override
    public void showMessage(String message) {
        statusBar.setText(message); // show the message in the status bar at the bottom of the window
        if (subjectsDialog != null && subjectsDialog.isDisplayable()) { // if the Subjects window is open and displayable
            subjectsDialog.setStatus(message); //show the message in the Subjects window too
        }
    }

    @Override
    public void run() {
        //The controller calls run() last, the window is already built and ready to be shown. The Controller does not block, it only makes the window visible.
        frame.setVisible(true);
    }

    @Override
    public void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today) {
        //creates a modal dialog that shows the recommendations for today. 
        //The user can move a task from there, and the dialog will call moveTask() to ask the Controller to do it.
        new TodayDialog(frame, ranked, subjectNames, today, this::moveTask).setVisible(true); //waits until closed
    }

    @Override
    public void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts) {
        //Called to open the window and also to refresh it after every change
        if (subjectsDialog == null || !subjectsDialog.isDisplayable()) { //not open
            subjectsDialog = new SubjectsDialog(frame, listener); //create the window and register the Controller as listener
            subjectsDialog.refill(subjects, taskCounts); //fill the window with the subjects and the number of tasks in each subject
            subjectsDialog.setVisible(true); //waits until closed
        } else {
            subjectsDialog.refill(subjects, taskCounts); //refresh the window with the subjects and the number of tasks in each subject
        }
    }

    @Override
    public void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today) {
        new CalendarDialog(frame, tasks, subjectNames, today).setVisible(true);
    }


    //////selection and movement

    //deselects all the other lists, so only one task is selected in the whole board. The list that keeps its selection is passed as parameter.
    private void clearOtherSelections(JList<Task> keep) {
        for (JList<Task> other : lists) {
            if (other != keep) {
                other.clearSelection(); //deselect all the other lists
            }
        }
    }

    //The selected task of the whole board, or null.
    private Task selectedTask() {
        for (JList<Task> list : lists) { //for each column, check if it has a selected task
            Task task = list.getSelectedValue(); // null if nothing is selected in that list
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    //The selected task: if there is none, it warns the user and returns null.
    private Task selectedTaskOrWarn() {
        Task task = selectedTask();
        if (task == null) {
            showMessage("Select a task first (click on a card).");
        }
        return task;
    }

    private Task findTask(int id) {
        for (DefaultListModel<Task> model : models) { //for each column, check if it has a task with the given id
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).getId() == id) {
                    return model.get(i);
                }
            }
        }
        return null;
    }

    // Moves the selected task to the next or previous column, depending on the direction (-1 = left, +1 = right).
    private void moveSelected(int direction) {
        Task task = selectedTaskOrWarn(); //select a task, or warn the user if there is none
        if (task == null) return;
        int target = task.getStatus().ordinal() + direction; //the target column is the current column + direction (-1 = left, +1 = right)
        if (target < 0 || target >= statuses.length) {
            showMessage("The task is already in the " + (direction > 0 ? "last" : "first") + " column.");
            return;
        }
        moveTask(task, statuses[target]);
    }

    //The only place where the View asks for a move (buttons, keys, Today and drag and drop all use it).
    private void moveTask(Task task, Status target) {
        pendingSelectId = task.getId(); //the selection will follow the task
        listener.onMoveTask(task.getId(), target); //the Controller changes the Model and redraws
    }

    //After redrawing, selects again the task that was moved or edited.
    private void restoreSelection() {
        if (pendingSelectId == null) return; //no task to select again
        for (int c = 0; c < models.size(); c++) {
            for (int i = 0; i < models.get(c).size(); i++) {
                if (models.get(c).get(i).getId() == pendingSelectId) { //for each column, for each task, if the id matches the pendingSelectId
                    lists.get(c).setSelectedIndex(i); // select the task in the list
                    lists.get(c).ensureIndexIsVisible(i); // scroll the list to make the task visible
                }
            }
        }
        pendingSelectId = null;
    }


    //create / edit / delete / details

    private void newTask() {
        if (subjectNames.isEmpty()) { // if there are no subjects, the user cannot create a task
            showMessage("There are no subjects yet. Open Subjects (F3) to create one.");
            return;
        }
        openForm(null);
    }

    private void editSelected() {
        Task task = selectedTaskOrWarn();
        if (task != null) openForm(task);
    }

    //existing == null = new task
    private void openForm(Task existing) {
        new TaskFormDialog(frame, existing, subjectNames, today, data -> {
            if (existing != null) {
                pendingSelectId = existing.getId(); //the selection will follow the task, even if it is moved to another column
                listener.onEditTask(existing.getId(), data); //ask the Controller to edit the task, it will redraw the board
            } else {
                listener.onAddTask(data); //ask the Controller to add the task, it will redraw the board
            }
        }).setVisible(true);
    }

    private void deleteSelected() {
        Task task = selectedTaskOrWarn();
        if (task == null) return;
        if (Ui.confirm(frame, "Delete task", "Delete \"" + task.getTitle() + "\"?\nYou can undo it with Undo.")) {
            listener.onDeleteTask(task.getId()); //the View never deletes anything, it asks the Controller
        } else {
            showMessage("Delete cancelled.");
        }
    }

    private void showDetails(Task task) {
        long days = task.daysUntilDeadline(today); // negative = overdue, 0 = today, positive = days left
        String when = days < 0 ? Math.abs(days) + " day(s) overdue" : days == 0 ? "due today" : "due in " + days + " day(s)";
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

    ////drag and drop, theme

    /**
     * Lets the user drag a card from this list and drop it on another column.
     * What travels is only the task id (as text). The drop target asks the Controller to move that task.
     */
    private void installDragAndDrop(JList<Task> list, int column) {
        list.setDragEnabled(true); // the user can drag a card from this list
        list.setDropMode(DropMode.INSERT); //insert the card where the mouse is, instead of swapping it with the card under the mouse
        list.setTransferHandler(new TransferHandler() { //setTransferHandler handles the drag and drop of cards between columns
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE; // the user can only move a card, not copy it
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                Task task = list.getSelectedValue(); //the value of the selected row
                return task == null ? null : new StringSelection(String.valueOf(task.getId())); 
                //the transferable is the task id as text, so the drop target can find the task and ask the Controller to move it
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor); // the drop target only accepts text (the task id)
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    String text = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor); // the task id as text
                    Task task = findTask(Integer.parseInt(text)); // find the task with that id, or null if it is not one of our cards
                    if (task == null) return false;
                    if (task.getStatus() != statuses[column]) { // dropping on its own column does nothing
                        moveTask(task, statuses[column]); // ask the Controller to move the task, it will redraw the board
                    }
                    return true;
                } catch (Exception e) {
                    return false; // not one of our cards
                }
            }
        });
    }

    //Columns are grey and cards are lighter: the colours come from the current theme.
    private void applyColumnColors() {
        Color columnColor = UIManager.getColor("Panel.background");
        for (JList<Task> list : lists) {
            list.setBackground(columnColor); // the column (behind the cards)
            list.getParent().setBackground(columnColor); // the viewport (empty area below the cards)
        }
    }

    private void toggleTheme() {
        dark = !dark; // switch between light and dark theme
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        for (Window w : Window.getWindows()) { // for each open window (the main window and any dialogs)
            SwingUtilities.updateComponentTreeUI(w); // repaint every open window with the new theme
        }
        applyColumnColors();
        themeButton.setText(dark ? "Light mode" : "Dark mode");
        frame.repaint();
    }
}
