package studyboard.model;

import java.time.*; // LocalDate
import java.util.*; // List, ArrayList, Map, LinkedHashMap, Optional, Collections

/**
 * Guarda TODAS las asignaturas y tareas EN MEMORIA y reparte ids únicos.
 * (Guardar en fichero lo hace FileStorage.) Sin código de pantalla.
 */
public class TaskRepository {
    private final List<Subject> subjects = new ArrayList<>(); // lista de asignaturas
    private final List<Task> tasks = new ArrayList<>();       // lista de tareas
    private int nextSubjectId = 1; // el siguiente id libre de asignatura
    private int nextTaskId = 1;    // el siguiente id libre de tarea

    // ------------------------------------------------------------------ leer

    /** Vistas de solo lectura: quien las recibe no puede añadir ni borrar por error. */
    public List<Subject> getSubjects() { return Collections.unmodifiableList(subjects); }
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }

    /** Optional = "quizá no existe". Obliga a quien llama a comprobarlo. */
    public Optional<Task> findTask(int id) {
        return tasks.stream().filter(t -> t.getId() == id).findFirst();
    }

    public Optional<Subject> findSubject(int id) {
        return subjects.stream().filter(s -> s.getId() == id).findFirst();
    }

    /** Mapa idAsignatura -> nombre (cómodo para dibujar las tareas). */
    public Map<Integer, String> getSubjectNames() {
        Map<Integer, String> names = new LinkedHashMap<>(); // LinkedHashMap mantiene el orden de inserción
        for (Subject s : subjects) {
            names.put(s.getId(), s.getName());
        }
        return names;
    }

    /** Mapa idAsignatura -> número de tareas de esa asignatura. */
    public Map<Integer, Integer> getTaskCounts() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Task t : tasks) {
            counts.merge(t.getSubjectId(), 1, Integer::sum); // suma 1, o empieza en 1
        }
        return counts;
    }

    // ------------------------------------------------------------------ tareas

    /** Crea una tarea con datos sueltos (la usa SampleData). */
    public Task addTask(String title, int subjectId, Priority priority,
                        LocalDate deadline, double estimatedHours) {
        Task task = new Task(nextTaskId, title, subjectId, priority, deadline, estimatedHours);
        nextTaskId++;   // solo se llega aquí si la creación no lanzó error
        tasks.add(task);
        return task;
    }

    /** Crea una tarea desde el formulario (empieza en "To do"). */
    public Task addTask(TaskData data) {
        Task task = addTask(data.title(), data.subjectId(), data.priority(),
                data.deadline(), data.estimatedHours());
        task.setNotes(data.notes());
        return task;
    }

    /** Sobrescribe los campos editables de una tarea existente. */
    public void updateTask(Task task, TaskData data) {
        task.setTitle(data.title());
        task.setNotes(data.notes());
        task.setSubjectId(data.subjectId());
        task.setPriority(data.priority());
        task.setDeadline(data.deadline());
        task.setEstimatedHours(data.estimatedHours());
    }

    /** Borra una tarea. Devuelve false si no había ninguna con ese id. */
    public boolean deleteTask(int id) {
        return tasks.removeIf(t -> t.getId() == id);
    }

    // ------------------------------------------------------------------ asignaturas

    public Subject addSubject(String name) {
        checkNameFree(name, -1); // los ids empiezan en 1, así que -1 significa "nadie"
        Subject subject = new Subject(nextSubjectId, name); // lanza error si el nombre es malo
        nextSubjectId++;
        subjects.add(subject);
        return subject;
    }

    /** Cambia el nombre (mismas reglas que al crear). */
    public void renameSubject(Subject subject, String newName) {
        checkNameFree(newName, subject.getId()); // la propia asignatura puede conservar su nombre
        subject.setName(newName);
    }

    /**
     * Borra la asignatura Y sus tareas (una tarea sin asignatura sería inválida).
     * Devuelve cuántas tareas se borraron para avisar al usuario.
     */
    public int deleteSubject(int id) {
        int before = tasks.size();
        tasks.removeIf(t -> t.getSubjectId() == id);
        subjects.removeIf(s -> s.getId() == id);
        return before - tasks.size();
    }

    /** Rechaza un nombre ya usado por OTRA asignatura (sin distinguir mayúsculas). */
    private void checkNameFree(String name, int exceptId) {
        if (name == null) return; // ese caso ya lo avisa la clase Subject
        String wanted = name.trim();
        for (Subject s : subjects) {
            if (s.getId() != exceptId && s.getName().equalsIgnoreCase(wanted)) {
                throw new IllegalArgumentException("A subject called \"" + s.getName() + "\" already exists.");
            }
        }
    }

    // ------------------------------------------------------------------ copias (para Undo)

    /** Copia PROFUNDA: tareas y asignaturas nuevas, no las mismas. Sirve de "foto" del estado. */
    public TaskRepository copy() {
        TaskRepository c = new TaskRepository(); // repositorio vacío
        c.restoreFrom(this);                     // lo rellenamos con copias de lo nuestro
        return c;
    }

    /** Sustituye TODO el contenido por una copia del de 'other' (así se deshace un cambio). */
    public void restoreFrom(TaskRepository other) {
        clear();
        for (Subject s : other.subjects) {
            restoreSubject(s.getId(), s.getName());
        }
        for (Task t : other.tasks) {
            // Creamos una tarea NUEVA igual que la original (si no, compartiríamos el mismo objeto)
            Task copy = new Task(t.getId(), t.getTitle(), t.getSubjectId(), t.getPriority(),
                    t.getDeadline(), t.getEstimatedHours());
            copy.setNotes(t.getNotes());
            copy.setStatus(t.getStatus());
            restoreTask(copy);
        }
        nextSubjectId = other.nextSubjectId; // los ids siguen donde iban
        nextTaskId = other.nextTaskId;
    }

    // ------------------------------------------------------------------ apoyo para cargar fichero

    /** Vacía todo (si el fichero cargado resulta inválido). */
    public void clear() {
        subjects.clear();
        tasks.clear();
        nextSubjectId = 1;
        nextTaskId = 1;
    }

    /** Añade una asignatura que YA tiene id (al leer el fichero). */
    public void restoreSubject(int id, String name) {
        if (subjects.stream().anyMatch(s -> s.getId() == id)) {
            throw new IllegalArgumentException("duplicate subject id " + id);
        }
        subjects.add(new Subject(id, name));
        nextSubjectId = Math.max(nextSubjectId, id + 1); // los ids nuevos no chocan con los cargados
    }

    /** Añade una tarea que YA tiene id (al leer el fichero). */
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
}
