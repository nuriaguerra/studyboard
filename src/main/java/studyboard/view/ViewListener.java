package studyboard.view;

import studyboard.model.*;

/**
 * Eventos que la View manda al Controller: "el usuario quiere X".
 * (La View NUNCA toca los datos: solo avisa por aquí.)
 * onAddSubject y onRenameSubject lanzan IllegalArgumentException con un mensaje
 * para el usuario si el nombre no vale; la View muestra ese mensaje.
 */
public interface ViewListener {
    void onMoveTask(int taskId, Status newStatus);   // mover tarea de columna
    void onAddTask(TaskData data);                   // crear tarea
    void onEditTask(int taskId, TaskData data);      // editar tarea
    void onDeleteTask(int taskId);                   // borrar tarea

    void onShowToday();                              // abrir pantalla Today
    void onShowSubjects();                           // abrir pantalla de asignaturas
    void onAddSubject(String name);                  // crear asignatura
    void onRenameSubject(int subjectId, String newName); // renombrar asignatura
    void onDeleteSubject(int subjectId);             // borrar asignatura

    void onUndo();                                   // deshacer el último cambio de datos

    /** El usuario cambió lo que muestra el tablero (TaskFilter.NONE = mostrar todo). */
    void onChangeView(TaskFilter filter, SortOrder sort);

        void onShowCalendar();   // abrir el calendario
}
