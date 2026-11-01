package studyboard.model;

/**
 * Task priority. Each value has a text label AND a symbol, so the UI
 * never depends on colour alone (accessibility).
 */
public enum Priority {
    LOW("Low", " "),
    MEDIUM("Medium", "!"),
    HIGH("High", "!!");

    private final String label;
    private final String symbol;

    Priority(String label, String symbol) {
        this.label = label;
        this.symbol = symbol;
    }

    public String getLabel() {
        return label;
    }

    public String getSymbol() {
        return symbol;
    }
}