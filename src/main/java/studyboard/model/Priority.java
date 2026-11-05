package studyboard.model; // package, folder where the class lives

/**
 * Priority of a task. Every value has a text and a symbol
 * The order of declaration matters: LOW < MEDIUM < HIGH, because enums are compared by their position, and SortOrder / TaskScorer rely on that.
 */
public enum Priority {
    // Each value calls the constructor below with (label, symbol)
    LOW("Low", " "), // no symbol, low priority tasks are not highlighted
    MEDIUM("Medium", "!"),
    HIGH("High", "!!");

    private final String label;  // text, "High"
    private final String symbol; // symbol, "!!" (shown at the start of each task line)

    Priority(String label, String symbol) { // constructor, it only stores both pieces of data
        this.label = label;
        this.symbol = symbol;
    }

    // Getters: read-only access to the private fields
    public String getLabel() { return label; }
    public String getSymbol() { return symbol; }
}
