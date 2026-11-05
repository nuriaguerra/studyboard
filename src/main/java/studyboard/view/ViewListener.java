package studyboard.view;

import studyboard.model.*;

/**
 * Events that the View sends to the Controller: "the user wants X".
 * (The View never touches the data, it only tells the Controller what the user wants to do.)
 * The Controller implements this interface
 */
public interface ViewListener {
    void onMoveTask(int taskId, Status newStatus); // move a task to another column
    void onAddTask(TaskData data);// create a task
    void onEditTask(int taskId, TaskData data);// edit a task
    void onDeleteTask(int taskId);  // delete a task

    void onShowToday(); // open the Today screen
    void onShowSubjects(); // open the Subjects screen
    void onAddSubject(String name); // create a subject
    void onRenameSubject(int subjectId, String newName); // rename a subject
    void onDeleteSubject(int subjectId); // delete a subject

    void onUndo();// undo the last change of data

    /** The user changed what the board shows (TaskFilter.NONE = show everything). */
    void onChangeView(TaskFilter filter, SortOrder sort);

    void onShowCalendar(); // open the calendar
}
