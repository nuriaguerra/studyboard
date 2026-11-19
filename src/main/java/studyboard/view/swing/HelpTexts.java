package studyboard.view.swing;

//The HelpTexts class contains the help text for each window, to be shown when the user presses F1. 
//It is a final class with a private constructor, so it cannot be instantiated or subclassed.
// All its fields are static final strings, so they can be accessed without creating an instance of the class.
final class HelpTexts {
    private HelpTexts() { }

    static final String BOARD = """
            MOUSE
              Click a card to select it. Double-click: its details.
              Drag a card to another column to move it.

            KEYBOARD
              Ctrl+N   New task            Ctrl+E   Edit the selected task
              Delete   Delete (asks first)
              Ctrl+Left / Ctrl+Right   Move the task to the previous / next column
              Ctrl+Z   Undo the last change (up to 20)
              Ctrl+F   Go to the search box (Enter applies it)
              F2   Today: what to do next and why
              F3   Subjects: create, rename, delete
              F4   Calendar: deadlines by month
              F1   This help (every window has its own)

            Dark mode: button at the top right.""";

    static final String TASK_FORM = """
              Tab / Shift+Tab   Next / previous field
              Enter             Save        Esc   Cancel

            Deadline: DD.MM.YYYY (for example 25.11.2026).
            Estimate: hours, for example 2.5 (0 to 100).""";

    static final String TODAY = """
            This window suggests the next task to work on.
            "Why now?" lists the rules that gave it points:
            deadline, priority, size of the task and whether
            you have already started it.

            "Start it" / "Mark as done" moves the task for you.
              Esc   Back to the board""";

    static final String SUBJECTS = """
              Click a subject, then use the buttons.
              Double-click a subject: rename it.
              Delete asks first, and its tasks are deleted too.
              Undo restores the last change.
              Esc   Close""";

    static final String FILTER = """
            Search text looks in title, notes and subject.
            All the conditions you set must match at the same time.
            "Clear filter" shows every task again.

              Enter   Apply        Esc   Cancel""";

    static final String CALENDAR = """
              Left / Right   Previous / next month
              T              Back to the current month
              Esc            Close

            Boxed bold day = today
            *2   = 2 tasks due that day, at least one still pending
            +1   = 1 task due that day, already done""";
}