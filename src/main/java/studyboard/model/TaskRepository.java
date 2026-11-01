package studyboard.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds all subjects and tasks IN MEMORY and hands out unique ids.
 * (Saving to a file will be added later.) No UI code here.
 */
public class TaskRepository {
    private final List<Subject> subjects = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private int nextSubjectId = 1;
    private int nextTaskId = 1;

    public Subject addSubject(String name) {
        Subject subject = new Subject(nextSubjectId, name); // may throw if name is empty
        nextSubjectId++;                                    // only reached if creation succeeded
        subjects.add(subject);
        return subject;
    }

    public Task addTask(String title, int subjectId, Priority priority,
                        LocalDate deadline, double estimatedHours) {
        Task task = new Task(nextTaskId, title, subjectId, priority, deadline, estimatedHours);
        nextTaskId++;
        tasks.add(task);
        return task;
    }

    /** Read-only views: callers cannot add or remove items by accident. */
    public List<Subject> getSubjects() { return Collections.unmodifiableList(subjects); }
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }

    /** Optional = "maybe there is no such task", which forces the caller to handle that case. */
    public Optional<Task> findTask(int id) {
        return tasks.stream().filter(t -> t.getId() == id).findFirst();
    }

    /** Map subjectId -> subject name, handy for displaying tasks. */
    public Map<Integer, String> getSubjectNames() {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (Subject s : subjects) {
            names.put(s.getId(), s.getName());
        }
        return names;
    }

        /** Creates a task from form data (starts in "To do"). */
    public Task addTask(TaskData data) {
        Task task = addTask(data.title(), data.subjectId(), data.priority(),
                data.deadline(), data.estimatedHours());
        task.setNotes(data.notes());
        return task;
    }

    /** Overwrites the editable fields of an existing task. */
    public void updateTask(Task task, TaskData data) {
        task.setTitle(data.title());
        task.setNotes(data.notes());
        task.setSubjectId(data.subjectId());
        task.setPriority(data.priority());
        task.setDeadline(data.deadline());
        task.setEstimatedHours(data.estimatedHours());
    }

    /** Removes a task. Returns false if there was no task with that id. */
    public boolean deleteTask(int id) {
        return tasks.removeIf(t -> t.getId() == id);
    }
}