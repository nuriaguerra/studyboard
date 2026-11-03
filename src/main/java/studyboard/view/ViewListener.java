package studyboard.view;

import studyboard.model.Status;
import studyboard.model.TaskData;

/**
 * Events the View sends to the Controller ("the user wants X").
 * onAddSubject and onRenameSubject throw IllegalArgumentException with a message
 * written for the user when the name is not valid; the View shows that message.
 */
public interface ViewListener {
    void onMoveTask(int taskId, Status newStatus);
    void onAddTask(TaskData data);
    void onEditTask(int taskId, TaskData data);
    void onDeleteTask(int taskId);

    /** The user wants the Today screen. The View also calls this once when the app starts. */
    void onShowToday();

    /** The user wants the Subjects screen. */
    void onShowSubjects();
    void onAddSubject(String name);
    void onRenameSubject(int subjectId, String newName);
    void onDeleteSubject(int subjectId);
}