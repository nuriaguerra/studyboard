package studyboard.view.lanterna;

import com.googlecode.lanterna.*;             // TerminalSize, SGR (negrita)
import com.googlecode.lanterna.gui2.*;        // Window, Panel, Label, Button, TextBox, ComboBox...
import com.googlecode.lanterna.gui2.dialogs.*; // MessageDialog, TextInputDialog
import com.googlecode.lanterna.input.*;       // KeyStroke, KeyType (teclas)
import com.googlecode.lanterna.screen.*;      // Screen, TerminalScreen
import com.googlecode.lanterna.terminal.*;    // Terminal, DefaultTerminalFactory
import java.io.*;                             // IOException
import java.time.*;                           // LocalDate
import java.time.format.*;                    // DateTimeFormatter, DateTimeParseException, ResolverStyle
import java.util.*;                           // List, ArrayList, Map
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import studyboard.model.*;
import studyboard.view.*;

/**
 * VIEW en modo texto con Lanterna. TODO el código de dibujo está aquí.
 * Regla para entender cualquier método: la View NUNCA cambia datos.
 * Solo llama a listener.onXxx(...) (el Controller) y dibuja lo que éste le manda con showXxx(...).
 *
 * Ideas de Lanterna que se usan:
 *  - Window: una ventana. Panel: contenedor de componentes. Label: texto. Button: botón.
 *  - gui.addWindowAndWait(w): muestra la ventana y ESPERA hasta que se cierre.
 *  - Una lambda "() -> algo" es código que se ejecuta más tarde (p. ej. al pulsar un botón).
 */
public class LanternaView implements View {
    // Formatos de fecha: 02.11 (corto) y 02.11.2026 (largo)
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // STRICT + "uuuu": 31.02.2026 se rechaza en vez de cambiarse en silencio a 28.02.2026
    private static final DateTimeFormatter DATE_INPUT =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    // ---- TEXTOS DE AYUDA (uno por pantalla: ayuda "contextual"). Un """ ... """ es un texto de varias líneas.
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

    private static final String TODAY_HELP = """
            This screen suggests the next task to work on.
            "Why now?" lists the rules that gave it points:
            deadline, priority, size of the task and whether
            you have already started it.

              Tab     Next button
              Enter   Press the selected button
              Esc     Back to the board

            "Start it" / "Mark as done" moves the task for you.""";

    private static final String SUBJECTS_HELP = """
              Up / Down   Choose a subject
              Enter       Menu: rename or delete
              N  New subject      R  Rename
              D  Delete (asks first; its tasks are deleted too)
              U  Undo the last change
              Esc         Back to the board""";

    private static final String TASK_FORM_HELP = """
              Tab / Shift+Tab   Next / previous field
              Enter             Press the selected button
              Esc               Cancel without saving

            Deadline: DD.MM.YYYY (for example 25.11.2026).
            Estimate: hours, for example 2.5 (0 to 100).""";

    private static final String FILTER_HELP = """
            Search text looks in title, notes and subject.
            All the conditions you set must match at the same time.
            "Clear filter" shows every task again.

              Tab   Next field        Esc   Cancel""";

    private static final String CALENDAR_HELP = """
              Left / Right   Previous / next month
              T              Back to the current month
              Esc            Back to the board

            [ 4]  = today
            *2    = 2 tasks due that day, at least one still pending
            +1    = 1 task due that day, already done""";

    private final boolean useSwing;  // true = ventana emulada aparte (--swing)
    private final BasicWindow window = new BasicWindow("StudyBoard - Board"); // ventana del tablero
    private ViewListener listener;   // el Controller (se asigna en setListener)
    private MultiWindowTextGUI gui;  // gestor de ventanas (se crea en run())

    // ---- Estado del tablero que está en pantalla ----
    private final ActionListBox[] lists = new ActionListBox[Status.values().length]; // 1 lista por columna
    private final List<List<Task>> columnTasks = new ArrayList<>(); // tareas de cada columna (mismo orden)
    private Label statusLine = new Label(" ");                      // línea de mensajes (feedback)
    private Map<Integer, String> subjectNames = Map.of();           // id asignatura -> nombre
    private LocalDate today = LocalDate.now();
    private Integer pendingSelectId; // tarea a re-seleccionar tras redibujar (null = ninguna)
    private BoardOptions options = new BoardOptions(TaskFilter.NONE, SortOrder.DEADLINE, 0); // filtro/orden actuales

    // ---- Estado de la pantalla Subjects (subjectsList es null mientras está cerrada) ----
    private ActionListBox subjectsList;
    private Label subjectsStatus;
    private List<Subject> shownSubjects = new ArrayList<>();
    private Map<Integer, Integer> subjectTaskCounts = Map.of();

    private YearMonth calMonth; // mes que muestra el calendario (YearMonth = año + mes, sin día)

    // ================================================================== constructor

    public LanternaView(boolean useSwing) {
        this.useSwing = useSwing;
        window.setHints(List.of(Window.Hint.FULL_SCREEN)); // la ventana ocupa todo el terminal

        // Teclas globales del tablero. onInput se llama ANTES de que el componente vea la tecla,
        // así que solo reaccionamos a nuestras letras y dejamos flechas/Enter/Tab en paz.
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                // Si no es una letra normal (o lleva Ctrl/Alt), la ignoramos
                if (key.getKeyType() != KeyType.Character || key.isCtrlDown() || key.isAltDown()) {
                    return;
                }
                // deliverEvent.set(false) = "esta tecla es mía, que no llegue al componente"
                switch (Character.toLowerCase(key.getCharacter())) { // minúscula: sirve N y n
                    case 'q' -> { deliverEvent.set(false); window.close(); }          // salir
                    case 'm' -> { deliverEvent.set(false); moveSelectedTask(+1); }    // mover a la derecha
                    case 'b' -> { deliverEvent.set(false); moveSelectedTask(-1); }    // mover a la izquierda
                    case 'n' -> { deliverEvent.set(false); showTaskForm(null); }      // nueva tarea
                    case 'e' -> { deliverEvent.set(false); editSelectedTask(); }      // editar
                    case 'd' -> { deliverEvent.set(false); deleteSelectedTask(); }    // borrar
                    case 't' -> { deliverEvent.set(false); listener.onShowToday(); }  // pantalla Today
                    case 's' -> { deliverEvent.set(false); listener.onShowSubjects(); } // asignaturas
                    case 'f' -> { deliverEvent.set(false); showFilterForm(); }        // filtro y orden
                    case '/' -> { deliverEvent.set(false); quickSearch(); }           // búsqueda rápida
                    case 'x' -> { deliverEvent.set(false); clearFilter(); }           // quitar filtro
                    case 'u' -> { deliverEvent.set(false); listener.onUndo(); }       // deshacer
                    case 'c' -> { deliverEvent.set(false); listener.onShowCalendar(); } // calendario
                    default -> { }                                                    // otras letras: nada
                }
            }
        });
        helpOnKey(window, "Help - Board", BOARD_HELP, true); // F1, H o ? abren la ayuda
    }

    @Override
    public void setListener(ViewListener listener) {
        this.listener = listener; // guardamos al Controller para poder avisarle
    }

    // ================================================================== tablero

    @Override
    public void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today,
                          BoardOptions options) {
        this.subjectNames = subjectNames; // guardamos lo recibido: lo usan otros métodos
        this.today = today;
        this.options = options;

        Status[] statuses = Status.values(); // TODO, IN_PROGRESS, DONE
        columnTasks.clear();

        // Un panel en rejilla con 3 columnas (una por estado), una al lado de la otra
        Panel board = new Panel(new GridLayout(statuses.length));
        for (int i = 0; i < statuses.length; i++) {
            List<Task> inColumn = new ArrayList<>();                       // tareas de esta columna
            ActionListBox list = new ActionListBox(new TerminalSize(28, 12)); // lista (ancho 28, alto 12)
            for (Task task : tasks) {
                if (task.getStatus() == statuses[i]) {
                    inColumn.add(task);
                    // La lambda se ejecuta al pulsar Enter sobre este elemento
                    list.addItem(formatTask(task), () -> showDetails(task));
                }
            }
            columnTasks.add(inColumn);
            lists[i] = list;

            // Marco con título "To do (3)" alrededor de la lista
            Border column = list.withBorder(Borders.singleLine(statuses[i].getLabel() + " (" + inColumn.size() + ")"));
            // FILL + true,true = la columna crece para aprovechar el espacio libre
            column.setLayoutData(GridLayout.createLayoutData(
                    GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, true));
            board.addComponent(column);
        }

        // Contenido total de la pantalla, de arriba abajo (un Panel es vertical por defecto)
        Panel root = new Panel();
        root.addComponent(new Label("Today: " + today.format(LONG_DATE)
                + "   |   Sort: " + options.sort().getLabel()
                + "   |   Showing " + tasks.size() + " of " + options.totalTasks() + " tasks"));
        // El filtro activo siempre es visible, en palabras y en negrita
        if (options.filter().isActive()) {
            root.addComponent(new Label("FILTER ON: " + options.filter().describe(subjectNames)
                    + "   (X: clear)").addStyle(SGR.BOLD));
        }
        if (tasks.isEmpty() && options.totalTasks() > 0) {
            root.addComponent(new Label("No task matches the current filter. Press F to change it or X to clear it."));
        }
        board.setLayoutData(LinearLayout.createLayoutData(
                LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow)); // el tablero ocupa el espacio libre
        root.addComponent(board);
        root.addComponent(new Label("Enter: details | N: new | E: edit | D: delete | M/B: move task | U: undo"));
        root.addComponent(new Label("/: search | F: filter and sort | X: clear filter | T: Today | S: subjects"));
        root.addComponent(new Label("C: calendar | H or F1: help | Q: quit"));
        statusLine = new Label(" "); // línea vacía donde saldrán los mensajes
        root.addComponent(statusLine);
        window.setComponent(root);   // ponemos todo en la ventana

        restoreSelection(); // dejamos el cursor sobre la tarea movida/editada
    }

    @Override
    public void showMessage(String message) {
        statusLine.setText(message);
        if (subjectsStatus != null) {      // si la pantalla Subjects está abierta, también ahí
            subjectsStatus.setText(message);
        }
    }

    @Override
    public void run() {
        Screen screen = null;
        try {
            // Terminal real por defecto; "--swing" abre una ventana emulada aparte
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setForceTextTerminal(!useSwing);
            factory.setPreferTerminalEmulator(useSwing);
            Terminal terminal = factory.createTerminal();

            screen = new TerminalScreen(terminal);
            screen.startScreen();                  // activa el modo texto
            gui = new MultiWindowTextGUI(screen);  // gestor de ventanas
            gui.addWindowAndWait(window);          // muestra el tablero y espera hasta que se cierre (Q)
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start the text user interface", e);
        } finally {
            // finally se ejecuta SIEMPRE: así el terminal vuelve a la normalidad aunque haya error
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException ignored) { }
            }
        }
    }

    // ================================================================== ayudas generales

    /** Una línea por tarea. Símbolo de prioridad + "LATE": el color nunca es la única señal. */
    private String formatTask(Task task) {
        String subject = subjectNames.getOrDefault(task.getSubjectId(), "?");
        String late = task.isOverdue(today) ? "LATE " : "";
        return String.format("%-2s %s%s (%s, %s)", task.getPriority().getSymbol(), late,
                task.getTitle(), subject, task.getDeadline().format(SHORT_DATE));
    }

    /** Hace que la tecla Esc cierre la ventana indicada (lo usan TODAS las ventanas secundarias). */
    private void closeOnEsc(BasicWindow w) {
        w.addWindowListener(new WindowListenerAdapter() {
            // onUnhandledInput solo recibe teclas que ningún componente usó
            // (así Esc sigue cerrando una lista desplegable abierta de forma normal)
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                if (key.getKeyType() == KeyType.Escape) {
                    w.close();
                    handled.set(true); // avisamos: esta tecla ya está usada
                }
            }
        });
    }

    /**
     * Abre la ayuda de una ventana con F1. Si letterKeys es true, también con H o ?.
     * (En formularios con cajas de texto letterKeys es false: si no, al escribir "h" saldría la ayuda.)
     * Se usa F1 Y letras porque algunos terminales se quedan la tecla F1 para su propia ayuda.
     */
    private void helpOnKey(BasicWindow w, String title, String text, boolean letterKeys) {
        w.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                boolean f1 = key.getKeyType() == KeyType.F1;
                boolean letter = letterKeys && key.getKeyType() == KeyType.Character
                        && !key.isCtrlDown() && !key.isAltDown()
                        && (key.getCharacter() == 'h' || key.getCharacter() == 'H'
                            || key.getCharacter() == '?');
                if (f1 || letter) {
                    deliverEvent.set(false); // la tecla es nuestra
                    MessageDialog.showMessageDialog(gui, title, text);
                }
            }
        });
    }

    /** Pregunta Sí/No (prevención de errores). "No" va primero: la respuesta segura es la inicial. */
    private boolean confirm(String title, String text) {
        MessageDialogButton answer = MessageDialog.showMessageDialog(gui, title, text,
                MessageDialogButton.No, MessageDialogButton.Yes);
        return answer == MessageDialogButton.Yes;
    }

    /** Diálogo con toda la información de una tarea (tecla Enter). */
    private void showDetails(Task task) {
        long days = task.daysUntilDeadline(today);
        // Operador ternario anidado: condición ? siSí : siNo
        String when = days < 0 ? Math.abs(days) + " day(s) overdue"
                : days == 0 ? "due today" : "due in " + days + " day(s)";
        String text = "Title:     " + task.getTitle()
                + "\nSubject:   " + subjectNames.getOrDefault(task.getSubjectId(), "?")
                + "\nStatus:    " + task.getStatus().getLabel()
                + "\nPriority:  " + task.getPriority().getLabel()
                + "\nDeadline:  " + task.getDeadline().format(LONG_DATE) + " (" + when + ")"
                + "\nEstimate:  " + task.getEstimatedHours() + " h"
                + "\nNotes:     " + (task.getNotes().isEmpty() ? "-" : task.getNotes());
        MessageDialog.showMessageDialog(gui, "Task details", text);
    }

    // ================================================================== selección y movimiento

    /** Tarea resaltada en la columna que tiene el foco, o null si no hay ninguna. */
    private Task selectedTask() {
        Interactable focused = window.getFocusedInteractable(); // componente con el foco
        for (int i = 0; i < lists.length; i++) {
            if (focused == lists[i]) {                           // ¿es la lista de esta columna?
                int index = lists[i].getSelectedIndex();         // posición resaltada
                if (index >= 0 && index < columnTasks.get(i).size()) {
                    return columnTasks.get(i).get(index);
                }
            }
        }
        return null;
    }

    /** Devuelve la tarea seleccionada; si no hay, avisa al usuario y devuelve null. */
    private Task selectedTaskOrWarn() {
        Task task = selectedTask();
        if (task == null) {
            showMessage("Select a task first (arrow keys).");
        }
        return task;
    }

    /** direction +1 = columna siguiente, -1 = anterior. Pide al Controller que haga el cambio. */
    private void moveSelectedTask(int direction) {
        Task task = selectedTaskOrWarn();
        if (task == null) return;
        int target = task.getStatus().ordinal() + direction; // ordinal = posición del enum (0,1,2)
        Status[] all = Status.values();
        if (target < 0 || target >= all.length) {
            showMessage("The task is already in the " + (direction > 0 ? "last" : "first") + " column.");
            return;
        }
        pendingSelectId = task.getId();                  // el cursor seguirá a la tarea
        listener.onMoveTask(task.getId(), all[target]);  // el Controller cambia el Model y redibuja
    }

    /** Tras redibujar, pone el cursor sobre la tarea recién movida o editada. */
    private void restoreSelection() {
        if (pendingSelectId == null) return;
        for (int c = 0; c < columnTasks.size(); c++) {
            for (int i = 0; i < columnTasks.get(c).size(); i++) {
                if (columnTasks.get(c).get(i).getId() == pendingSelectId) {
                    window.setFocusedInteractable(lists[c]); // damos el foco a esa columna
                    lists[c].setSelectedIndex(i);            // y resaltamos la tarea
                }
            }
        }
        pendingSelectId = null;
    }

    // ================================================================== crear / editar / borrar

    private void editSelectedTask() {
        Task task = selectedTaskOrWarn();
        if (task != null) showTaskForm(task);
    }

    private void deleteSelectedTask() {
        Task task = selectedTaskOrWarn();
        if (task == null) return;
        if (confirm("Delete task", "Delete \"" + task.getTitle() + "\"?")) {
            listener.onDeleteTask(task.getId());
        } else {
            showMessage("Delete cancelled.");
        }
    }

    /** Formulario. existing == null significa "tarea nueva"; si no, "editar esta tarea". */
    private void showTaskForm(Task existing) {
        if (subjectNames.isEmpty()) {
            showMessage("There are no subjects yet. Press S to create one.");
            return;
        }
        boolean editing = existing != null;
        BasicWindow form = new BasicWindow(editing ? "Edit task" : "New task");
        form.setHints(List.of(Window.Hint.CENTERED)); // ventana centrada

        // Las dos listas tienen el MISMO orden (el mapa conserva el orden de inserción)
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> subjectLabels = new ArrayList<>(subjectNames.values());
        List<String> priorityLabels = new ArrayList<>();
        for (Priority p : Priority.values()) {
            priorityLabels.add(p.getLabel());
        }

        // Campos de entrada, rellenos si estamos editando
        TextBox title = new TextBox(new TerminalSize(40, 1), editing ? existing.getTitle() : "");
        ComboBox<String> subject = new ComboBox<String>(subjectLabels);   // lista desplegable
        ComboBox<String> priority = new ComboBox<String>(priorityLabels);
        LocalDate defaultDate = editing ? existing.getDeadline() : today.plusDays(7);
        TextBox date = new TextBox(new TerminalSize(14, 1), defaultDate.format(LONG_DATE));
        TextBox hours = new TextBox(new TerminalSize(8, 1),
                editing ? String.valueOf(existing.getEstimatedHours()) : "1.0");
        TextBox notes = new TextBox(new TerminalSize(40, 1), editing ? existing.getNotes() : "");

        if (editing) { // preseleccionamos los valores de la tarea
            int subjectIndex = subjectIds.indexOf(existing.getSubjectId());
            if (subjectIndex >= 0) subject.setSelectedIndex(subjectIndex);
            priority.setSelectedIndex(existing.getPriority().ordinal());
        } else {
            priority.setSelectedIndex(Priority.MEDIUM.ordinal()); // por defecto: media
        }

        Button save = new Button("Save", () -> {
            try {
                // Crear TaskData VALIDA todo: si algo está mal lanza IllegalArgumentException
                TaskData data = new TaskData(title.getText(), notes.getText(),
                        subjectIds.get(subject.getSelectedIndex()),
                        Priority.values()[priority.getSelectedIndex()],
                        parseDate(date.getText()), parseHours(hours.getText()));
                if (editing) {
                    pendingSelectId = existing.getId(); // el cursor se queda en la tarea editada
                    listener.onEditTask(existing.getId(), data);
                } else {
                    listener.onAddTask(data);
                }
                form.close();
            } catch (IllegalArgumentException e) {
                // El formulario sigue abierto para que el usuario lo corrija
                MessageDialog.showMessageDialog(gui, "Please check the form", e.getMessage());
            }
        });
        Button cancel = new Button("Cancel", form::close); // form::close = "llama a form.close()"

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL)); // botones en fila
        buttons.addComponent(save);
        buttons.addComponent(cancel);
        buttons.setLayoutData(GridLayout.createLayoutData(
                GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false, 2, 1)); // ocupa 2 columnas

        Panel panel = new Panel(new GridLayout(2)); // rejilla de 2 columnas: etiqueta | campo
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
        panel.addComponent(new EmptySpace()); // hueco vacío en la rejilla
        panel.addComponent(buttons);
        form.setComponent(panel);

        closeOnEsc(form);           // Esc cancela
        helpOnKey(form, "Help - Task form", TASK_FORM_HELP, false); // solo F1
        gui.addWindowAndWait(form); // se queda aquí hasta que el formulario se cierra
    }

    /** Texto -> fecha, con un mensaje que dice exactamente qué está mal. */
    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_INPUT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The deadline must be a real date written as DD.MM.YYYY, for example "
                    + today.plusDays(7).format(LONG_DATE) + ".");
        }
    }

    /** Texto -> horas. Acepta "2.5" y "2,5". */
    private double parseHours(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The estimate must be a number of hours, for example 2.5.");
        }
    }

    // ================================================================== pantalla Today

    @Override
    public void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today) {
        this.subjectNames = subjectNames; // formatTask() usa estos dos campos
        this.today = today;

        BasicWindow todayWindow = new BasicWindow("StudyBoard - Today");
        todayWindow.setHints(List.of(Window.Hint.CENTERED));

        Panel panel = new Panel();
        panel.addComponent(new Label("Today: " + today.format(LONG_DATE)));
        panel.addComponent(new EmptySpace());

        Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));

        if (ranked.isEmpty()) {
            panel.addComponent(new Label("Nothing pending. All your tasks are done!"));
        } else {
            Recommendation next = ranked.get(0); // la mejor sugerencia
            Task task = next.task();

            // La sugerencia, en una caja con borde
            Panel card = new Panel();
            card.addComponent(new Label(task.getTitle()).addStyle(SGR.BOLD));
            card.addComponent(new Label(subjectNames.getOrDefault(task.getSubjectId(), "?")
                    + " | due " + task.getDeadline().format(LONG_DATE)
                    + " | " + task.getEstimatedHours() + " h"
                    + " | " + task.getPriority().getLabel() + " priority"));
            card.addComponent(new EmptySpace());
            card.addComponent(new Label("Why now?").addStyle(SGR.BOLD));
            for (String reason : next.reasons()) {            // un renglón por cada motivo
                card.addComponent(new Label("  - " + reason));
            }
            card.addComponent(new Label("Score: " + next.score()));
            panel.addComponent(card.withBorder(Borders.singleLine("Next up")));

            // Las 4 siguientes sugerencias, una línea cada una
            if (ranked.size() > 1) {
                panel.addComponent(new EmptySpace());
                panel.addComponent(new Label("Also coming up").addStyle(SGR.BOLD));
                for (int i = 1; i < Math.min(5, ranked.size()); i++) {
                    panel.addComponent(new Label("  " + formatTask(ranked.get(i).task())));
                }
            }

            // Acción principal. Ambos botones piden al Controller; la View no cambia el Model.
            if (task.getStatus() == Status.TODO) {
                buttons.addComponent(new Button("Start it", () -> {
                    pendingSelectId = task.getId(); // el cursor del tablero seguirá a la tarea
                    listener.onMoveTask(task.getId(), Status.IN_PROGRESS);
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
        todayWindow.setComponent(panel);

        closeOnEsc(todayWindow);           // Esc = volver al tablero
        helpOnKey(todayWindow, "Help - Today", TODAY_HELP, true);
        gui.addWindowAndWait(todayWindow); // espera hasta que se cierre
    }

    // ================================================================== pantalla Subjects

    @Override
    public void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts) {
        this.shownSubjects = new ArrayList<>(subjects); // copia: la lista del Model puede cambiar
        this.subjectTaskCounts = taskCounts;

        // Ya está abierta (el Controller la refresca tras cada cambio): solo rellenamos la lista
        if (subjectsList != null) {
            fillSubjectsList();
            return;
        }

        subjectsList = new ActionListBox(new TerminalSize(46, 10));
        subjectsStatus = new Label(shownSubjects.isEmpty() ? "No subjects yet. Press N to add one." : " ");
        BasicWindow subjectsWindow = new BasicWindow("StudyBoard - Subjects");
        subjectsWindow.setHints(List.of(Window.Hint.CENTERED));
        fillSubjectsList();

        // Sin botones: así el foco NUNCA sale de la lista y la asignatura resaltada es siempre la correcta.
        // (Con botones, al bajar hasta ellos la selección pasaba por la última asignatura.)
        Panel panel = new Panel();
        panel.addComponent(new Label("Your subjects (number of tasks on the right)"));
        panel.addComponent(subjectsList.withBorder(Borders.singleLine("Subjects")));
        panel.addComponent(new Label("Enter: menu | N: new | R: rename | D: delete | U: undo | H: help | Esc: back"));
        panel.addComponent(subjectsStatus);
        subjectsWindow.setComponent(panel);

        closeOnEsc(subjectsWindow); // Esc cierra
        helpOnKey(subjectsWindow, "Help - Subjects", SUBJECTS_HELP, true);

        // Atajos de letra de esta ventana
        subjectsWindow.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                if (key.getKeyType() != KeyType.Character || key.isCtrlDown() || key.isAltDown()) {
                    return;
                }
                switch (Character.toLowerCase(key.getCharacter())) {
                    case 'n' -> { deliverEvent.set(false); newSubject(); }
                    case 'r' -> { deliverEvent.set(false); renameSelectedSubject(); }
                    case 'd' -> { deliverEvent.set(false); deleteSelectedSubject(); }
                    // Deshacer y refrescar la lista (la pantalla sigue abierta, solo se rellena)
                    case 'u' -> { deliverEvent.set(false); listener.onUndo(); listener.onShowSubjects(); }
                    default -> { }
                }
            }
        });

        try {
            gui.addWindowAndWait(subjectsWindow); // espera hasta que se cierre
        } finally {
            subjectsList = null;   // "la pantalla está cerrada" para showSubjects() y showMessage()
            subjectsStatus = null;
        }
    }

    /** Rellena la lista desde shownSubjects y deja el cursor cerca de donde estaba. */
    private void fillSubjectsList() {
        int previous = subjectsList.getSelectedIndex();
        subjectsList.clearItems();
        for (Subject subject : shownSubjects) {
            int count = subjectTaskCounts.getOrDefault(subject.getId(), 0);
            // Enter sobre un elemento = renombrarlo
            subjectsList.addItem(String.format("%-30s %3d task(s)", subject.getName(), count),
                    () -> subjectMenu(subject)); // Enter = menú de acciones
        }
        if (!shownSubjects.isEmpty()) {
            // Math.max/min evitan que el índice se salga de la lista
            subjectsList.setSelectedIndex(Math.max(0, Math.min(previous, shownSubjects.size() - 1)));
        }
    }

    /** Asignatura resaltada, o null. */
    private Subject selectedSubject() {
        int index = subjectsList.getSelectedIndex();
        return (index >= 0 && index < shownSubjects.size()) ? shownSubjects.get(index) : null;
    }

    private void newSubject() {
        subjectNameDialog("New subject", "", name -> {
            listener.onAddSubject(name);
            // El Controller ya refrescó la lista: ponemos el cursor en la nueva (la última)
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

    /** Enter sobre una asignatura: un pequeño menú con las acciones posibles. */
    private void subjectMenu(Subject subject) {
        new ActionListDialogBuilder()
                .setTitle(subject.getName())             // título = nombre de la asignatura
                .setDescription("Choose an action")
                .addAction("Rename", () -> renameSubject(subject)) // cada opción ejecuta su lambda
                .addAction("Delete", () -> deleteSubject(subject)) // con ESA asignatura
                .build()
                .showDialog(gui);                        // se muestra y espera
    }

    /** Pide un nuevo nombre y se lo manda al Controller. */
    private void renameSubject(Subject subject) {
        subjectNameDialog("Rename subject", subject.getName(),
                name -> listener.onRenameSubject(subject.getId(), name));
    }

    /**
     * Pide un nombre y ejecuta 'action' con él. Si el nombre no vale (el Model lanza
     * IllegalArgumentException) se muestra el mensaje y se vuelve a preguntar con el mismo
     * texto, para que el usuario no tenga que reescribirlo.
     */
    private void subjectNameDialog(String dialogTitle, String initialText, Consumer<String> action) {
        String text = initialText;
        while (true) { // se repite hasta que haya éxito o se cancele
            text = TextInputDialog.showDialog(gui, dialogTitle, "Subject name:", text);
            if (text == null) {        // Cancelar o Esc
                showMessage("Cancelled.");
                return;
            }
            try {
                action.accept(text);   // ejecuta lo que nos pasaron (crear o renombrar)
                return;                // éxito
            } catch (IllegalArgumentException e) {
                MessageDialog.showMessageDialog(gui, "Please check the name", e.getMessage());
            }
        }
    }

    /** Tecla D: borra la asignatura resaltada. */
    private void deleteSelectedSubject() {
        Subject subject = selectedSubject();
        if (subject == null) {
            showMessage("Select a subject first (arrow keys).");
            return;
        }
        deleteSubject(subject);
    }

    /** Pide confirmación (diciendo cuántas tareas se pierden) y avisa al Controller. */
    private void deleteSubject(Subject subject) {
        int count = subjectTaskCounts.getOrDefault(subject.getId(), 0);
        String text = (count == 0)
                ? "Delete the subject \"" + subject.getName() + "\"?"
                : "Delete the subject \"" + subject.getName() + "\" and its " + count
                        + " task(s)?\nThis cannot be undone.";
        if (confirm("Delete subject", text)) {
            listener.onDeleteSubject(subject.getId());
        } else {
            showMessage("Delete cancelled.");
        }
    }

    // ================================================================== búsqueda y filtro

    /** Tecla "/": un diálogo pequeño. Conserva el resto de ajustes del filtro. */
    private void quickSearch() {
        String text = TextInputDialog.showDialog(gui, "Search",
                "Look for (title, notes or subject). Empty = no search:", options.filter().text());
        if (text == null) { // Cancelar o Esc
            showMessage("Search cancelled.");
            return;
        }
        listener.onChangeView(options.filter().withText(text), options.sort());
    }

    /** Tecla "X": quita el filtro pero conserva el orden. */
    private void clearFilter() {
        if (!options.filter().isActive()) {
            showMessage("No filter is active.");
            return;
        }
        listener.onChangeView(TaskFilter.NONE, options.sort());
    }

    /** Tecla "F": formulario completo. Empieza con los valores actuales. */
    private void showFilterForm() {
        TaskFilter current = options.filter();
        BasicWindow form = new BasicWindow("Search, filter and sort");
        form.setHints(List.of(Window.Hint.CENTERED));

        // Listas desplegables. El índice 0 de asignatura/prioridad significa "sin restricción"
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> subjectLabels = new ArrayList<>();
        subjectLabels.add("All subjects");
        subjectLabels.addAll(subjectNames.values()); // mismo orden que subjectIds (desplazado 1)
        List<String> priorityLabels = new ArrayList<>();
        priorityLabels.add("Any priority");
        for (Priority p : Priority.values()) {
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
        CheckBox hideDone = new CheckBox("Hide them"); // casilla
        hideDone.setChecked(current.hideDone());

        if (current.subjectId() != null) {
            int index = subjectIds.indexOf(current.subjectId());
            if (index >= 0) subject.setSelectedIndex(index + 1); // +1 por "All subjects"
        }
        if (current.priority() != null) {
            priority.setSelectedIndex(current.priority().ordinal() + 1);
        }
        sort.setSelectedIndex(options.sort().ordinal());

        Button apply = new Button("Apply", () -> {
            // Índice 0 = "sin restricción" = null
            Integer subjectId = subject.getSelectedIndex() == 0
                    ? null : subjectIds.get(subject.getSelectedIndex() - 1);
            Priority chosenPriority = priority.getSelectedIndex() == 0
                    ? null : Priority.values()[priority.getSelectedIndex() - 1];
            listener.onChangeView(
                    new TaskFilter(text.getText(), subjectId, chosenPriority, hideDone.isChecked()),
                    SortOrder.values()[sort.getSelectedIndex()]);
            form.close();
        });
        Button clear = new Button("Clear filter", () -> {
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
        helpOnKey(form, "Help - Filter", FILTER_HELP, false); // solo F1
        gui.addWindowAndWait(form);
    }

        // ================================================================== calendario

    @Override
    public void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today) {
        this.subjectNames = subjectNames; // formatTask() usa estos dos campos
        this.today = today;
        calMonth = YearMonth.from(today); // empezamos en el mes actual

        BasicWindow cal = new BasicWindow("StudyBoard - Calendar");
        cal.setHints(List.of(Window.Hint.CENTERED));
        drawMonth(cal, tasks); // dibuja el mes dentro de la ventana

        closeOnEsc(cal);                                        // Esc vuelve al tablero
        helpOnKey(cal, "Help - Calendar", CALENDAR_HELP, true); // H, ? o F1
        // Esta ventana no tiene botones ni cajas: las flechas llegan a onUnhandledInput
        cal.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                if (key.getKeyType() == KeyType.ArrowLeft) {
                    calMonth = calMonth.minusMonths(1);     // mes anterior
                } else if (key.getKeyType() == KeyType.ArrowRight) {
                    calMonth = calMonth.plusMonths(1);      // mes siguiente
                } else if (key.getKeyType() == KeyType.Character
                        && Character.toLowerCase(key.getCharacter()) == 't') {
                    calMonth = YearMonth.from(today);       // volver a hoy
                } else {
                    return;                                 // otra tecla: no es nuestra
                }
                drawMonth(cal, tasks);                      // redibujamos con el mes nuevo
                handled.set(true);                          // la tecla ya está usada
            }
        });
        gui.addWindowAndWait(cal); // espera hasta que se cierre
    }

    /** Construye el contenido de la ventana para el mes guardado en calMonth. */
    private void drawMonth(BasicWindow cal, List<Task> tasks) {
        Panel root = new Panel();
        // Título: "November 2026" en negrita
        root.addComponent(new Label(calMonth.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + calMonth.getYear())
                .addStyle(SGR.BOLD));

        Panel grid = new Panel(new GridLayout(7)); // rejilla de 7 columnas = 7 días
        for (String d : new String[] {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}) {
            grid.addComponent(new Label(String.format("%-6s", d))); // cabecera, 6 caracteres de ancho
        }
        // Huecos antes del día 1. getValue(): lunes = 1 ... domingo = 7
        for (int i = 1; i < calMonth.atDay(1).getDayOfWeek().getValue(); i++) {
            grid.addComponent(new EmptySpace());
        }
        for (int day = 1; day <= calMonth.lengthOfMonth(); day++) {
            LocalDate date = calMonth.atDay(day);
            int total = 0, pending = 0;                       // tareas del día / sin terminar
            for (Task t : tasks) {
                if (t.getDeadline().equals(date)) {
                    total++;
                    if (t.getStatus() != Status.DONE) pending++;
                }
            }
            String mark = total == 0 ? "  " : (pending > 0 ? "*" : "+") + total; // *2 pendiente, +1 hecho
            boolean isToday = date.equals(today);
            // Hoy entre corchetes: [ 4]. %2d = número en 2 espacios. Cada celda mide 6 caracteres
            grid.addComponent(new Label((isToday ? "[" : " ") + String.format("%2d", day)
                    + (isToday ? "]" : " ") + mark + (mark.length() == 2 ? "" : " ")));
        }
        root.addComponent(grid);

        // Lista de tareas de ESTE mes, ordenadas por fecha
        List<Task> inMonth = tasks.stream()
                .filter(t -> YearMonth.from(t.getDeadline()).equals(calMonth))
                .sorted(Comparator.comparing(Task::getDeadline)).toList();
        root.addComponent(new EmptySpace());
        root.addComponent(new Label(inMonth.isEmpty() ? "No deadlines this month."
                : "Deadlines this month:").addStyle(SGR.BOLD));
        for (int i = 0; i < Math.min(8, inMonth.size()); i++) { // máximo 8 líneas
            Task t = inMonth.get(i);
            root.addComponent(new Label("  " + formatTask(t)    // reutilizamos el formato del tablero
                    + (t.getStatus() == Status.DONE ? " [done]" : "")));
        }
        if (inMonth.size() > 8) {
            root.addComponent(new Label("  ... and " + (inMonth.size() - 8) + " more"));
        }
        root.addComponent(new EmptySpace());
        root.addComponent(new Label("Left/Right: month | T: this month | H: help | Esc: back"));
        cal.setComponent(root); // sustituye el contenido anterior de la ventana
    }
}
