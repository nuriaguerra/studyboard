package studyboard.view;

import studyboard.model.Status;

/**
 * Events the View sends to the Controller ("the user wants X").
 * The View never changes the Model itself: it only reports what the user asked for.
 */
public interface ViewListener {
    void onMoveTask(int taskId, Status newStatus);
}