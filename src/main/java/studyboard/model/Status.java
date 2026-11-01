package studyboard.model;

/** The three columns of the Kanban board. An enum is a fixed list of allowed values. */
public enum Status {
    TODO("To do"),
    IN_PROGRESS("In progress"),
    DONE("Done");

    private final String label; // text shown to the user

    Status(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}