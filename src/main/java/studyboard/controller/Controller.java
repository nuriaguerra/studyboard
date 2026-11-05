package studyboard.controller;

import java.io.*; // IOException
import java.time.*; // LocalDate
import java.util.*; // List, ArrayList, Map, Objects, Deque, ArrayDeque
import studyboard.model.*;
import studyboard.view.*;

/**
 * Controller that receives what the user does (ViewListener), changes the Model and tells the View what to draw.
 * It has no Lanterna code: it only knows the View interface.
 * The flow of every action is always the same:
 *   user presses a key -> View calls listener.onXxx(...) -> Controller changes the Model
 *   -> Controller saves the file -> Controller calls view.showXxx(...) to redraw.
 */
public class Controller implements ViewListener { // implements the interface that the View calls when the user does something
    private final TaskRepository repository; // data in memory
    private final View view;  // the screen (any class that implements View)
    private final FileStorage storage; // data on disk

    // What the board shows now. It is not saved in the file, every start shows all the tasks.
    private TaskFilter filter = TaskFilter.NONE;
    private SortOrder sortOrder = SortOrder.DEADLINE;

    // undo, a stack of previous states of the repository. The top is the most recent.
    // Deque = double-ended queue; push puts an item on top and pop takes the top one out.
    private final Deque<TaskRepository> undoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 20; // maximum number of changes that can be undone

    public Controller(TaskRepository repository, View view, FileStorage storage) {
        this.repository = repository;
        this.view = view;
        this.storage = storage;
        this.view.setListener(this); // "tell me when the user does something" (this = this Controller)
    }

    /** Loads the data, draws the first screen and hands control to the View until the user quits. */
    public void start() {
        String startupMessage = loadData(); // null if everything was normal
        refreshBoard(); // draw the board first
        if (startupMessage != null) {
            view.showMessage(startupMessage); // and the message after (a redraw creates a new empty message line)
        }
        view.run(); //we stay here until the user presses Q
    }

    ///////first loading

    /** Returns a message for the user, or null if everything was normal. */
    private String loadData() {
        // First run: there is no file yet -> start with example data
        if (!storage.exists()) {
            String warning = loadSampleDataAndSave(); // returns a warning if saving fails
            return warning != null ? warning : "Welcome to StudyBoard! Example data loaded. Your changes are saved automatically.";
        }
        try {
            storage.load(repository); //load the file into the repository (the Model in memory)
            return null;
        } catch (IOException e) {
            // The file is unreadable or damaged: we keep it as a backup and start again
            repository.clear(); //clear the repository so we can load the sample data
            try {
                String backupName = storage.backupBrokenFile().getFileName().toString(); // we keep the broken file as a backup
                String warning = loadSampleDataAndSave(); // returns a warning if saving fails
                return warning != null ? warning : "The data file was damaged. It was kept as " + backupName + ". Example data loaded.";
            } catch (IOException backupError) {
                return "WARNING: the data file is damaged and cannot be backed up: " + backupError.getMessage();
            }
        }
    }

    /** Loads the example data and saves it. Returns a warning text, or null if all went well. */
    private String loadSampleDataAndSave() {
        SampleData.load(repository); // load the example data into the repository (the Model in memory)
        try {
            storage.save(repository); // save the example data to the file (the Model on disk)
            return null;
        } catch (IOException e) {
            return "WARNING: cannot save to " + storage.getFile() + " (" + e.getMessage() + ")";
        }
    }

    ///// board

    /** Applies the current filter and order and sends the result to the View. Returns how many tasks are shown. */
    private int refreshBoard() {
        Map<Integer, String> names = repository.getSubjectNames();
        List<Task> shown = new ArrayList<>(); // a copy, the repository's own list is never modified
        for (Task task : repository.getTasks()) { // we iterate over the tasks in the repository (the Model in memory)
            if (filter.matches(task, names)) { // the filter decides if the task is visible, if not, it is hidden (but not deleted)
                shown.add(task);
            }
        }
        shown.sort(sortOrder.getComparator()); // sorts with the chosen rule
        // The view shows the tasks, the subject names, today's date, and the current filter and order (BoardOptions)
        view.showBoard(shown, names, LocalDate.now(), new BoardOptions(filter, sortOrder, repository.getTasks().size()));
        return shown.size();
    }

    /**
     * Every change ends here: redraw, save, and tell the user the result.
     * If saving fails, the user sees a warning instead of the success message.
     */
    private void saveAndRefresh(String successMessage) {
        refreshBoard(); // redraw first, so the user sees the change even if saving fails
        try {
            storage.save(repository); // save the repository (the Model in memory) to the file (the Model on disk)
            view.showMessage(successMessage);
        } catch (IOException e) {
            view.showMessage("WARNING: your change could not be saved (" + e.getMessage() + ").");
        }
    }

    /** Extra sentence for the message when the current filter hides this task ("" if it is visible). */
    private String hiddenNote(Task task) {
        return filter.matches(task, repository.getSubjectNames()) ? "" : " It is hidden by the current filter.";
    }

    ////// Undo

    /** Stores the state 'before'. It is only called when the change succeeded. */
    private void remember(TaskRepository before) {
        undoStack.push(before); // we can go back to this state with onUndo()
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast(); // we discard the oldest one
        }
    }

    @Override //method of the interface: the View calls this when the user presses U
    public void onUndo() {
        if (undoStack.isEmpty()) { //if there is nothing to undo, we just tell the user and return
            view.showMessage("Nothing to undo.");
            return;
        }
        repository.restoreFrom(undoStack.pop()); //we go back to the previous state
        
        // If the filter pointed to a subject that no longer exists, we remove that part of the filter
        if (filter.subjectId() != null && repository.findSubject(filter.subjectId()).isEmpty()) {
            filter = new TaskFilter(filter.text(), null, filter.priority(), filter.hideDone());
        }
        saveAndRefresh("Last change undone (" + undoStack.size() + " more can be undone).");
    }

    ////////actions on tasks
    

    @Override // method of the interface: the View calls this when the user moves a task to another column
    public void onMoveTask(int taskId, Status newStatus) {

        // ifPresent: the lambda only runs if the task exists (findTask returns an Optional)
        repository.findTask(taskId).ifPresent(task -> {
            TaskRepository before = repository.copy(); // we copy the repository before the change, so we can undo it later
            task.setStatus(newStatus); // we change the Model in memory
            remember(before); // we store the previous state so we can undo it later
            saveAndRefresh("Moved \"" + task.getTitle() + "\" to " + newStatus.getLabel() + "." + hiddenNote(task));
        });
    }

    @Override // method of the interface: the View calls this when the user adds a new task
    public void onAddTask(TaskData data) {
        TaskRepository before = repository.copy();
        Task task = repository.addTask(data); // throws an error if invalid: the View shows the message
        remember(before);
        saveAndRefresh("Added \"" + task.getTitle() + "\" to To do." + hiddenNote(task));
    }

    @Override
    public void onEditTask(int taskId, TaskData data) {
        repository.findTask(taskId).ifPresent(task -> {
            TaskRepository before = repository.copy();
            // we copy the repository before the change, so we can undo it later
            repository.updateTask(task, data);
            remember(before);
            saveAndRefresh("Saved changes to \"" + task.getTitle() + "\"." + hiddenNote(task));
        });
    }

    @Override
    public void onDeleteTask(int taskId) {
        repository.findTask(taskId).ifPresent(task -> {
            TaskRepository before = repository.copy();
            repository.deleteTask(taskId);
            remember(before);
            saveAndRefresh("Deleted \"" + task.getTitle() + "\".");
        });
    }

    @Override
    public void onChangeView(TaskFilter newFilter, SortOrder newSort) {
        // If null arrives we use the default values (condition ? ifTrue : ifFalse)
        this.filter = (newFilter == null) ? TaskFilter.NONE : newFilter;
        this.sortOrder = (newSort == null) ? SortOrder.DEADLINE : newSort;
        int shown = refreshBoard();
        int total = repository.getTasks().size(); // the total number of tasks in the repository (the Model in memory)
        if (filter.isActive()) { // if the filter is active, we tell the user how many tasks are shown and how many are hidden
            view.showMessage("Filter applied: showing " + shown + " of " + total + " tasks.");
        } else {
            view.showMessage("Showing all " + total + " tasks.");
        }
    }

    @Override
    public void onShowToday() {
        LocalDate today = LocalDate.now();
        // Today looks at all the tasks, the board filter does not hide a deadline from you
        view.showToday(TaskScorer.rank(repository.getTasks(), today), repository.getSubjectNames(), today);
    }

    //////subjects

    @Override
    public void onShowSubjects() {
        view.showSubjects(repository.getSubjects(), repository.getTaskCounts()); // taskCounts: subjectId -> number of tasks
    }

    @Override
    public void onAddSubject(String name) {
        TaskRepository before = repository.copy();
        Subject subject = repository.addSubject(name); // throws an error if invalid: the View shows the message
        remember(before);
        saveAndRefresh("Added subject \"" + subject.getName() + "\".");
        onShowSubjects(); // refresh the Subjects screen (it is open)
    }

    @Override
    public void onRenameSubject(int subjectId, String newName) {
        repository.findSubject(subjectId).ifPresent(subject -> { // ifPresent: the lambda only runs if the subject exists
            TaskRepository before = repository.copy();
            repository.renameSubject(subject, newName); // it can throw an error (repeated or empty name)
            remember(before);
            saveAndRefresh("Renamed subject to \"" + subject.getName() + "\".");
            onShowSubjects();
        });
    }

    @Override
    public void onDeleteSubject(int subjectId) {
        repository.findSubject(subjectId).ifPresent(subject -> {
            String name = subject.getName(); // we keep it for the message
            TaskRepository before = repository.copy();

            // If the board was filtered by this subject, we drop that part of the filter
            if (Objects.equals(filter.subjectId(), subjectId)) {
                filter = new TaskFilter(filter.text(), null, filter.priority(), filter.hideDone());
            }
            int removedTasks = repository.deleteSubject(subjectId); // returns how many tasks were deleted along with the subject
            remember(before);
            saveAndRefresh("Deleted subject \"" + name + "\" and " + removedTasks + " task(s).");
            onShowSubjects();
        });
    }

    @Override
    public void onShowCalendar() {
        // The Controller only hands over the data
        view.showCalendar(repository.getTasks(), repository.getSubjectNames(), LocalDate.now());
    }
}
