package studyboard.view;

import studyboard.model.Status;
import studyboard.model.TaskData;

/** Events the View sends to the Controller ("the user wants X"). */
public interface ViewListener {
    void onMoveTask(int taskId, Status newStatus);
    void onAddTask(TaskData data);
    void onEditTask(int taskId, TaskData data);
    void onDeleteTask(int taskId);
}