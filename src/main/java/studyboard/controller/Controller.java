package studyboard.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import studyboard.model.FileStorage;
import studyboard.model.SampleData;
import studyboard.model.Status;
import studyboard.model.Task;
import studyboard.model.TaskData;
import studyboard.model.TaskRepository;
import studyboard.view.View;
import studyboard.view.ViewListener;
import studyboard.model.TaskScorer;
import studyboard.model.Subject;

/** Connects Model and View. Contains NO Lanterna code (it only knows the View interface). */
public class Controller implements ViewListener {
    private final TaskRepository repository;
    private final View view;
    private final FileStorage storage;

    public Controller(TaskRepository repository, View view, FileStorage storage) {
        this.repository = repository;
        this.view = view;
        this.storage = storage;
        this.view.setListener(this); // "tell me when the user does something"
    }

    /** Loads the data, shows the first screen and hands control to the View until the user quits. */
    public void start() {
        String startupMessage = loadData();
        refreshBoard();
        if (startupMessage != null) {
            view.showMessage(startupMessage);
        }
        view.run();
    }

    // ------------------------------------------------------------------ loading

    /** Returns a message for the user, or null if everything was normal. */
    private String loadData() {
        // First run: no file yet -> start with example data.
        if (!storage.exists()) {
            String warning = loadSampleDataAndSave();
            return warning != null ? warning
                    : "Welcome to StudyBoard! Example data loaded. Your changes are saved automatically.";
        }
        try {
            storage.load(repository);
            return null;
        } catch (IOException e) {
            // The file is unreadable or damaged: keep it as a backup and start again.
            repository.clear(); // the load may have filled the repository halfway
            try {
                String backupName = storage.backupBrokenFile().getFileName().toString();
                String warning = loadSampleDataAndSave();
                return warning != null ? warning
                        : "The data file was damaged. It was kept as " + backupName + ". Example data loaded.";
            } catch (IOException backupError) {
                return "WARNING: the data file is damaged and cannot be backed up: " + backupError.getMessage();
            }
        }
    }

    /** Loads the example data and saves it. Returns a warning text, or null if all went well. */
    private String loadSampleDataAndSave() {
        SampleData.load(repository);
        try {
            storage.save(repository);
            return null;
        } catch (IOException e) {
            return "WARNING: cannot save to " + storage.getFile() + " (" + e.getMessage() + ")";
        }
    }

    // ------------------------------------------------------------------ board

    /** Sorts tasks (earliest deadline first, then highest priority) and sends them to the View. */
    private void refreshBoard() {
        List<Task> sorted = new ArrayList<>(repository.getTasks()); // copy: do not modify the original
        sorted.sort(Comparator.comparing(Task::getDeadline)
                .thenComparing(Task::getPriority, Comparator.reverseOrder()));
        view.showBoard(sorted, repository.getSubjectNames(), LocalDate.now());
    }

    /**
     * Every change of the data ends here: redraw, save, and tell the user the result.
     * If saving fails the user sees a warning instead of the success message.
     */
    private void saveAndRefresh(String successMessage) {
        refreshBoard();
        try {
            storage.save(repository);
            view.showMessage(successMessage);
        } catch (IOException e) {
            view.showMessage("WARNING: your change could not be saved (" + e.getMessage() + ").");
        }
    }

    // ------------------------------------------------------------------ user actions

    @Override
    public void onMoveTask(int taskId, Status newStatus) {
        repository.findTask(taskId).ifPresent(task -> {
            task.setStatus(newStatus);
            saveAndRefresh("Moved \"" + task.getTitle() + "\" to " + newStatus.getLabel() + ".");
        });
    }

    @Override
    public void onAddTask(TaskData data) {
        Task task = repository.addTask(data);
        saveAndRefresh("Added \"" + task.getTitle() + "\" to To do.");
    }

    @Override
    public void onEditTask(int taskId, TaskData data) {
        repository.findTask(taskId).ifPresent(task -> {
            repository.updateTask(task, data);
            saveAndRefresh("Saved changes to \"" + task.getTitle() + "\".");
        });
    }

    @Override
    public void onDeleteTask(int taskId) {
        repository.findTask(taskId).ifPresent(task -> {
            repository.deleteTask(taskId);
            saveAndRefresh("Deleted \"" + task.getTitle() + "\".");
        });
    }

    @Override
    public void onShowToday() {
        LocalDate today = LocalDate.now();
        // The Model decides WHAT to suggest; the View only decides how to show it.
        view.showToday(TaskScorer.rank(repository.getTasks(), today),
                repository.getSubjectNames(), today);
    }

    @Override
    public void onShowSubjects() {
        view.showSubjects(repository.getSubjects(), repository.getTaskCounts());
    }

    @Override
    public void onAddSubject(String name) {
        Subject subject = repository.addSubject(name); // throws if invalid: the View shows the message
        saveAndRefresh("Added subject \"" + subject.getName() + "\".");
        onShowSubjects(); // refresh the Subjects screen (it is open)
    }

    @Override
    public void onRenameSubject(int subjectId, String newName) {
        repository.findSubject(subjectId).ifPresent(subject -> {
            repository.renameSubject(subject, newName);
            saveAndRefresh("Renamed subject to \"" + subject.getName() + "\".");
            onShowSubjects();
        });
    }

    @Override
    public void onDeleteSubject(int subjectId) {
        repository.findSubject(subjectId).ifPresent(subject -> {
            String name = subject.getName(); // keep it for the message
            int removedTasks = repository.deleteSubject(subjectId);
            saveAndRefresh("Deleted subject \"" + name + "\" and " + removedTasks + " task(s).");
            onShowSubjects();
        });
    }
}