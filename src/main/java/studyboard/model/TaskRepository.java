package studyboard.model;

import java.time.*; //LocalDate
import java.util.*; //List, ArrayList, Map, LinkedHashMap, Optional, Collections

/**
 * Holds all the subjects and tasks in memory and hands out unique ids.
 * The Controller is the only class that changes this object.
 */
public class TaskRepository {
    private final List<Subject> subjects = new ArrayList<>(); //list of subjects
    private final List<Task> tasks = new ArrayList<>(); //list of tasks
    private int nextSubjectId = 1; //the next free subject id (ids start at 1 and only grow)
    private int nextTaskId = 1; // the next free task id

    //////read

    /** Read-only views: whoever receives them cannot add or remove items by mistake. */
    public List<Subject> getSubjects() { return Collections.unmodifiableList(subjects); }
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }

    /**
     * Maybe there is no such task, so the Optional type forces the caller to handle that case,
     * which is safer than returning null and forgetting to check it.
     */
    public Optional<Task> findTask(int id) {
        return tasks.stream().filter(t -> t.getId() == id).findFirst(); //stream go through the list and find the first task with that id
    }

    public Optional<Subject> findSubject(int id) {
        return subjects.stream().filter(s -> s.getId() == id).findFirst();
    }

    /** Map subjectId -> subject name, handy to display the tasks (a Task only stores the subject id). */
    public Map<Integer, String> getSubjectNames() {
        Map<Integer, String> names = new LinkedHashMap<>(); // LinkedHashMap keeps the insertion order
        for (Subject s : subjects) {
            names.put(s.getId(), s.getName()); //put the subject id and name in the map
        }
        return names;
    }

    /** Map subjectId -> number of tasks of that subject (subjects with 0 tasks do not appear). */
    public Map<Integer, Integer> getTaskCounts() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Task t : tasks) {
            counts.merge(t.getSubjectId(), 1, Integer::sum); // add 1 to the old value, or start at 1
        }
        return counts;
    }

    //////tasks

    /** Creates a task from separate values (SampleData uses it). */
    public Task addTask(String title, int subjectId, Priority priority, LocalDate deadline, double estimatedHours) {
        Task task = new Task(nextTaskId, title, subjectId, priority, deadline, estimatedHours);
        nextTaskId++;   //we only get here if the creation did not throw an error
        tasks.add(task); //add the new task to the list
        return task;
    }

    /** Creates a task from the form data (it starts in "To do"). Same method name, different parameters = overloading. */
    public Task addTask(TaskData data) {
        Task task = addTask(data.title(), data.subjectId(), data.priority(), data.deadline(), data.estimatedHours());
        task.setNotes(data.notes()); //the first addTask has no notes parameter, so we set them here
        return task;
    }

    /** Overwrites the editable fields of an existing task (id and status stay the same). */
    public void updateTask(Task task, TaskData data) {
        task.setTitle(data.title());
        task.setNotes(data.notes());
        task.setSubjectId(data.subjectId());
        task.setPriority(data.priority());
        task.setDeadline(data.deadline());
        task.setEstimatedHours(data.estimatedHours());
        //set the fields of the task to the new values from the form data
    }

    /** Removes a task. Returns false if there was no task with that id (removeIf returns true if it removed something). */
    public boolean deleteTask(int id) {
        return tasks.removeIf(t -> t.getId() == id);
    }

    /////subjects

    public Subject addSubject(String name) {
        checkNameFree(name, -1); //ids start at 1, so -1 means "nobody"
        Subject subject = new Subject(nextSubjectId, name); // throws an error if the name is empty or too long
        nextSubjectId++; //we only get here if the creation did not throw an error
        subjects.add(subject);
        return subject;
    }

    /** Changes the name (same rules as when creating one). */
    public void renameSubject(Subject subject, String newName) {
        checkNameFree(newName, subject.getId()); // the subject itself may keep its own name
        subject.setName(newName);
    }

    /**
     * Deletes the subject and all its tasks (a task without a subject would be invalid).
     * Returns how many tasks were deleted, so the screen can tell the user.
     */
    public int deleteSubject(int id) {
        int before = tasks.size(); // how many tasks there are now
        tasks.removeIf(t -> t.getSubjectId() == id); // remove the tasks of this subject
        subjects.removeIf(s -> s.getId() == id); // remove the subject itself
        return before - tasks.size(); // difference = tasks that disappeared
    }

    /** Rejects a name already used by another subject (ignoring upper/lower case). */
    private void checkNameFree(String name, int exceptId) {
        if (name == null) return; // the Subject class reports this case
        String wanted = name.trim(); // we ignore spaces at the start and the end
        for (Subject s : subjects) {
            if (s.getId() != exceptId && s.getName().equalsIgnoreCase(wanted)) {
                throw new IllegalArgumentException("A subject called \"" + s.getName() + "\" already exists.");
            }
        }
    }

    ///// copies (for Undo)

    /** deep copy: new tasks and new subjects, not the same objects. It is a "photo" of the current state. */
    public TaskRepository copy() {
        TaskRepository c = new TaskRepository(); // empty repository
        c.restoreFrom(this); // we fill it with copies of our own data
        return c;
    }

    /** Replaces all the content with a copy of the content of 'other' (this is how a change is undone). */
    public void restoreFrom(TaskRepository other) {
        clear(); // start from zero
        for (Subject s : other.subjects) { // a class can read the private fields of another object of the same class
            restoreSubject(s.getId(), s.getName()); //restoreSubject() creates a new Subject object, so we do not share the same object
        }
        for (Task t : other.tasks) {
            // We create a new task equal to the original (if not, both repositories would share the same object
            //and changing one would change the other)
            Task copy = new Task(t.getId(), t.getTitle(), t.getSubjectId(), t.getPriority(), t.getDeadline(), t.getEstimatedHours());
            copy.setNotes(t.getNotes());
            copy.setStatus(t.getStatus());
            restoreTask(copy); // restoreTask() adds the new task to the list, so we do not share the same object
        }
        nextSubjectId = other.nextSubjectId; // the ids continue where they were
        nextTaskId = other.nextTaskId;
    }

    ///// support for loading a file

    /** Empties everything (also used if a loaded file turns out to be invalid). */
    public void clear() {
        subjects.clear(); //clear the lists, but do not change the objects themselves
        tasks.clear();
        nextSubjectId = 1; //we start again with id 1, because the old subjects and tasks are gone
        nextTaskId = 1;
    }

    /** Adds a subject that already has an id (used when reading the file and when copying). */
    public void restoreSubject(int id, String name) {
        if (subjects.stream().anyMatch(s -> s.getId() == id)) { // anyMatch = "is there at least one"
            throw new IllegalArgumentException("duplicate subject id " + id);
        }
        subjects.add(new Subject(id, name));
        nextSubjectId = Math.max(nextSubjectId, id + 1); // new ids must not collide with the loaded ones
    }

    /** Adds a task that already has an id (used when reading the file and when copying). */
    public void restoreTask(Task task) {
        if (tasks.stream().anyMatch(t -> t.getId() == task.getId())) { // if there is already a task with that id, it is an error
            throw new IllegalArgumentException("duplicate task id " + task.getId());
        }
        if (subjects.stream().noneMatch(s -> s.getId() == task.getSubjectId())) { // noneMatch = "there is no subject like that"
            throw new IllegalArgumentException("task refers to an unknown subject " + task.getSubjectId());
        }
        tasks.add(task);
        nextTaskId = Math.max(nextTaskId, task.getId() + 1);
    }
}
