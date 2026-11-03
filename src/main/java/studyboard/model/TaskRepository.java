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
        checkNameFree(name, -1);                            // ids start at 1, so -1 means "nobody"
        Subject subject = new Subject(nextSubjectId, name); // may throw if name is empty or too long
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

        /** Removes everything (used if a loaded file turns out to be invalid). */
    public void clear() {
        subjects.clear();
        tasks.clear();
        nextSubjectId = 1;
        nextTaskId = 1;
    }

    /** Adds a subject that already has an id (used when loading a file). */
    public void restoreSubject(int id, String name) {
        if (subjects.stream().anyMatch(s -> s.getId() == id)) {
            throw new IllegalArgumentException("duplicate subject id " + id);
        }
        subjects.add(new Subject(id, name));
        nextSubjectId = Math.max(nextSubjectId, id + 1); // new ids must not collide with loaded ones
    }

    /** Adds a task that already has an id (used when loading a file). */
    public void restoreTask(Task task) {
        if (tasks.stream().anyMatch(t -> t.getId() == task.getId())) {
            throw new IllegalArgumentException("duplicate task id " + task.getId());
        }
        if (subjects.stream().noneMatch(s -> s.getId() == task.getSubjectId())) {
            throw new IllegalArgumentException("task refers to an unknown subject " + task.getSubjectId());
        }
        tasks.add(task);
        nextTaskId = Math.max(nextTaskId, task.getId() + 1);
    }

        // ------------------------------------------------------------------ subjects management

    /** Optional = "maybe there is no such subject". */
    public Optional<Subject> findSubject(int id) {
        return subjects.stream().filter(s -> s.getId() == id).findFirst();
    }

    /** Changes the name of a subject (same rules as when creating one). */
    public void renameSubject(Subject subject, String newName) {
        checkNameFree(newName, subject.getId()); // the subject itself may keep its own name
        subject.setName(newName);
    }

    /**
     * Deletes a subject AND all its tasks (a task without a subject would be invalid).
     * Returns how many tasks were deleted, so the UI can tell the user.
     */
    public int deleteSubject(int id) {
        int before = tasks.size();
        tasks.removeIf(t -> t.getSubjectId() == id);
        subjects.removeIf(s -> s.getId() == id);
        return before - tasks.size();
    }

    /** Map subjectId -> number of tasks of that subject. */
    public Map<Integer, Integer> getTaskCounts() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Task t : tasks) {
            counts.merge(t.getSubjectId(), 1, Integer::sum); // add 1, or start at 1
        }
        return counts;
    }

    /** Rejects a name already used by ANOTHER subject (ignoring upper/lower case). */
    private void checkNameFree(String name, int exceptId) {
        if (name == null) {
            return; // the Subject class reports this case
        }
        String wanted = name.trim();
        for (Subject s : subjects) {
            if (s.getId() != exceptId && s.getName().equalsIgnoreCase(wanted)) {
                throw new IllegalArgumentException("A subject called \"" + s.getName() + "\" already exists.");
            }
        }
    }
}