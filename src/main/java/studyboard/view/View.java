package studyboard.view;

import java.time.*;   // LocalDate
import java.util.*;   // List, Map
import studyboard.model.*;

/**
 * INTERFAZ = lista de métodos que una pantalla DEBE tener. Aquí no hay código, solo nombres.
 * El Controller solo conoce esta interfaz, no Lanterna.
 * Proyecto 1: la implementa LanternaView. Proyecto 2: la implementará una GUI.
 */
public interface View {
    /** Registra quién debe enterarse de lo que hace el usuario (el Controller). */
    void setListener(ViewListener listener);

    /**
     * Dibuja el tablero Kanban con las tareas ya filtradas y ordenadas.
     * subjectNames: idAsignatura -> nombre. options: filtro y orden actuales.
     */
    void showBoard(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today, BoardOptions options);

    /** Pantalla Today. 'ranked' viene ordenada: el primero es la sugerencia. */
    void showToday(List<Recommendation> ranked, Map<Integer, String> subjectNames, LocalDate today);

    /**
     * Pantalla de asignaturas. taskCounts: idAsignatura -> nº de tareas.
     * Si ya está abierta, solo refresca la lista y vuelve enseguida.
     */
    void showSubjects(List<Subject> subjects, Map<Integer, Integer> taskCounts);

    /** Línea corta de feedback para el usuario ("visibilidad del estado del sistema"). */
    void showMessage(String message);

    /** Abre la interfaz y se queda esperando hasta que el usuario sale. */
    void run();

    /** Calendario mensual. Recibe TODAS las tareas: el filtro del tablero no oculta fechas límite. */
    void showCalendar(List<Task> tasks, Map<Integer, String> subjectNames, LocalDate today);
}
