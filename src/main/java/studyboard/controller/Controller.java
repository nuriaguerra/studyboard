package studyboard.controller;

import java.io.*;    // IOException
import java.time.*;  // LocalDate
import java.util.*;  // List, ArrayList, Map, Objects
import studyboard.model.*;
import studyboard.view.*;

/**
 * CONTROLLER: el intermediario. Recibe lo que hace el usuario (ViewListener),
 * cambia el Model y le dice a la View qué dibujar.
 * No tiene NADA de Lanterna: solo conoce la interfaz View.
 */
public class Controller implements ViewListener { // "implements" = cumple la interfaz
    private final TaskRepository repository; // datos en memoria
    private final View view;                 // la pantalla (cualquiera que cumpla View)
    private final FileStorage storage;       // datos en disco

    // Lo que muestra el tablero ahora. No se guarda en el fichero: cada arranque muestra todo.
    private TaskFilter filter = TaskFilter.NONE;
    private SortOrder sortOrder = SortOrder.DEADLINE;

    // UNDO: pila de "fotos" del estado ANTES de cada cambio (la más reciente arriba).
    // Deque = cola doble; push apila arriba y pop saca la de arriba.
    private final Deque<TaskRepository> undoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 20; // máximo de cambios que se pueden deshacer

    public Controller(TaskRepository repository, View view, FileStorage storage) {
        this.repository = repository;
        this.view = view;
        this.storage = storage;
        this.view.setListener(this); // "avísame cuando el usuario haga algo"
    }

    /** Carga datos, dibuja la primera pantalla y cede el control a la View hasta que se sale. */
    public void start() {
        String startupMessage = loadData(); // null si todo fue normal
        refreshBoard();
        if (startupMessage != null) {
            view.showMessage(startupMessage);
        }
        view.run(); // se queda aquí hasta que el usuario pulsa Q
    }

    // ------------------------------------------------------------------ carga inicial

    /** Devuelve un mensaje para el usuario, o null si todo fue normal. */
    private String loadData() {
        // Primera vez: no hay fichero -> datos de ejemplo
        if (!storage.exists()) {
            String warning = loadSampleDataAndSave();
            return warning != null ? warning
                    : "Welcome to StudyBoard! Example data loaded. Your changes are saved automatically.";
        }
        try {
            storage.load(repository);
            return null;
        } catch (IOException e) {
            // Fichero ilegible o dañado: lo guardamos como copia y empezamos de nuevo
            repository.clear(); // la carga pudo dejar el repositorio a medias
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

    /** Carga los datos de ejemplo y los guarda. Devuelve un aviso, o null si todo fue bien. */
    private String loadSampleDataAndSave() {
        SampleData.load(repository);
        try {
            storage.save(repository);
            return null;
        } catch (IOException e) {
            return "WARNING: cannot save to " + storage.getFile() + " (" + e.getMessage() + ")";
        }
    }

    // ------------------------------------------------------------------ tablero

    /** Aplica filtro y orden actuales y manda el resultado a la View. Devuelve cuántas tareas se ven. */
    private int refreshBoard() {
        Map<Integer, String> names = repository.getSubjectNames();
        List<Task> shown = new ArrayList<>(); // copia: la lista del repositorio no se modifica
        for (Task task : repository.getTasks()) {
            if (filter.matches(task, names)) {
                shown.add(task);
            }
        }
        shown.sort(sortOrder.getComparator()); // ordena con la regla elegida
        view.showBoard(shown, names, LocalDate.now(),
                new BoardOptions(filter, sortOrder, repository.getTasks().size()));
        return shown.size();
    }

    /**
     * Todo cambio de datos termina aquí: redibuja, guarda y avisa al usuario.
     * Si guardar falla, se muestra un aviso en vez del mensaje de éxito.
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

    /** Frase extra si el filtro actual esconde esta tarea ("" si se ve). */
    private String hiddenNote(Task task) {
        return filter.matches(task, repository.getSubjectNames())
                ? "" : " It is hidden by the current filter.";
    }

    // ------------------------------------------------------------------ Undo

    /** Guarda la foto 'before' (estado anterior). Solo se llama cuando el cambio salió bien. */
    private void remember(TaskRepository before) {
        undoStack.push(before);
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast(); // descartamos la más antigua
        }
    }

    @Override
    public void onUndo() {
        if (undoStack.isEmpty()) {
            view.showMessage("Nothing to undo.");
            return;
        }
        repository.restoreFrom(undoStack.pop()); // volvemos al estado anterior
        // Si el filtro apuntaba a una asignatura que ya no existe, quitamos esa parte del filtro
        if (filter.subjectId() != null && repository.findSubject(filter.subjectId()).isEmpty()) {
            filter = new TaskFilter(filter.text(), null, filter.priority(), filter.hideDone());
        }
        saveAndRefresh("Last change undone (" + undoStack.size() + " more can be undone).");
    }

    // ------------------------------------------------------------------ acciones sobre tareas
    // Patrón de cada cambio: 1) foto 'before'  2) cambiar el Model  3) remember(before)  4) guardar y redibujar.
    // Si el paso 2 lanza error (dato inválido), no se llega a remember: no queda un Undo vacío.

    @Override // "estoy implementando un método de la interfaz"
    public void onMoveTask(int taskId, Status newStatus) {
        // ifPresent: solo se ejecuta si la tarea existe
        repository.findTask(taskId).ifPresent(task -> {
            TaskRepository before = repository.copy();
            task.setStatus(newStatus);
            remember(before);
            saveAndRefresh("Moved \"" + task.getTitle() + "\" to " + newStatus.getLabel() + "."
                    + hiddenNote(task));
        });
    }

    @Override
    public void onAddTask(TaskData data) {
        TaskRepository before = repository.copy();
        Task task = repository.addTask(data);
        remember(before);
        saveAndRefresh("Added \"" + task.getTitle() + "\" to To do." + hiddenNote(task));
    }

    @Override
    public void onEditTask(int taskId, TaskData data) {
        repository.findTask(taskId).ifPresent(task -> {
            TaskRepository before = repository.copy();
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
        // Si llega null usamos los valores por defecto (condición ? siA : siB)
        this.filter = (newFilter == null) ? TaskFilter.NONE : newFilter;
        this.sortOrder = (newSort == null) ? SortOrder.DEADLINE : newSort;
        int shown = refreshBoard();
        int total = repository.getTasks().size();
        if (filter.isActive()) {
            view.showMessage("Filter applied: showing " + shown + " of " + total + " tasks.");
        } else {
            view.showMessage("Showing all " + total + " tasks.");
        }
    }

    @Override
    public void onShowToday() {
        LocalDate today = LocalDate.now();
        // Today mira TODAS las tareas: el filtro del tablero no esconde una fecha límite
        view.showToday(TaskScorer.rank(repository.getTasks(), today),
                repository.getSubjectNames(), today);
    }

    // ------------------------------------------------------------------ asignaturas

    @Override
    public void onShowSubjects() {
        view.showSubjects(repository.getSubjects(), repository.getTaskCounts());
    }

    @Override
    public void onAddSubject(String name) {
        TaskRepository before = repository.copy();
        Subject subject = repository.addSubject(name); // lanza error si es inválido: la View muestra el mensaje
        remember(before);
        saveAndRefresh("Added subject \"" + subject.getName() + "\".");
        onShowSubjects(); // refresca la pantalla de asignaturas (está abierta)
    }

    @Override
    public void onRenameSubject(int subjectId, String newName) {
        repository.findSubject(subjectId).ifPresent(subject -> {
            TaskRepository before = repository.copy();
            repository.renameSubject(subject, newName); // puede lanzar error (nombre repetido o vacío)
            remember(before);
            saveAndRefresh("Renamed subject to \"" + subject.getName() + "\".");
            onShowSubjects();
        });
    }

    @Override
    public void onDeleteSubject(int subjectId) {
        repository.findSubject(subjectId).ifPresent(subject -> {
            String name = subject.getName(); // lo guardamos para el mensaje
            TaskRepository before = repository.copy();
            // Si el tablero estaba filtrado por esta asignatura, quitamos esa parte del filtro:
            // si no, el usuario vería un tablero vacío.
            if (Objects.equals(filter.subjectId(), subjectId)) {
                filter = new TaskFilter(filter.text(), null, filter.priority(), filter.hideDone());
            }
            int removedTasks = repository.deleteSubject(subjectId);
            remember(before);
            saveAndRefresh("Deleted subject \"" + name + "\" and " + removedTasks + " task(s).");
            onShowSubjects();
        });
    }

    @Override
    public void onShowCalendar() {
        // El Controller solo entrega los datos; dibujarlos es cosa de la View
        view.showCalendar(repository.getTasks(), repository.getSubjectNames(), LocalDate.now());
    }
}
