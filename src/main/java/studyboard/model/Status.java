package studyboard.model;

/**
 * The 3 columns of the Kanban board.
 * An enum is a fixed list of allowed values: nobody can create a new Status later,
 * so a task can never be in a column that does not exist.
 */
public enum Status {

    // Each value carries a nice text in brackets, which is passed to the constructor below.
    // todo is the name used inside the code, "To do" is the text the user sees on the screen.
    // The ORDER matters: LanternaView uses ordinal() (0, 1, 2) to know which column is next.
    TODO("To do"),
    IN_PROGRESS("In progress"),
    DONE("Done"); // the semicolon ends the list of values and starts the normal members

    private final String label; //text shown to the user (final = it never changes after creation)

    // Enum constructor, Java calls it once per value above (todo, in progress, done).
    // Enum constructors are always private, so nobody can add values later.
    Status(String label) {
        this.label = label;
    }

    // Getter: the field is private, so this public method lets other classes read the text.
    public String getLabel() {
        return label;
    }
}
