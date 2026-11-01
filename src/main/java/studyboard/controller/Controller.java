package studyboard.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import studyboard.model.Status;
import studyboard.model.Task;
import studyboard.model.TaskRepository;
import studyboard.view.View;
import studyboard.view.ViewListener;

/** Connects Model and View. Contains NO Lanterna code (it only knows the View interface). */
public class Controller implements ViewListener {
    private final TaskRepository repository;
    private final View view;

    public Controller(TaskRepository repository, View view) {
        this.repository = repository;
        this.view = view;
        this.view.setListener(this); // "tell me when the user does something"
    }

    /** Shows the first screen and hands control to the View until the user quits. */
    public void start() {
        refreshBoard();
        view.run();
    }

    /** Sorts tasks (earliest deadline first, then highest priority) and sends them to the View. */
    private void refreshBoard() {
        List<Task> sorted = new ArrayList<>(repository.getTasks()); // copy: do not modify the original
        sorted.sort(Comparator.comparing(Task::getDeadline)
                .thenComparing(Task::getPriority, Comparator.reverseOrder()));
        view.showBoard(sorted, repository.getSubjectNames(), LocalDate.now());
    }

    @Override
    public void onMoveTask(int taskId, Status newStatus) {
        repository.findTask(taskId).ifPresent(task -> {
            task.setStatus(newStatus);
            refreshBoard();
            view.showMessage("Moved \"" + task.getTitle() + "\" to " + newStatus.getLabel() + ".");
        });
    }
}