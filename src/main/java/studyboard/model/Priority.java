package studyboard.model;

/**
 * Prioridad de una tarea. Cada valor tiene texto Y símbolo:
 * así la pantalla no depende solo del color (accesibilidad).
 * El orden de declaración importa: LOW < MEDIUM < HIGH (se usa para ordenar).
 */
public enum Priority {
    LOW("Low", " "),
    MEDIUM("Medium", "!"),
    HIGH("High", "!!");

    private final String label;  // texto: "High"
    private final String symbol; // símbolo: "!!"

    Priority(String label, String symbol) { // constructor: guarda ambos datos
        this.label = label;
        this.symbol = symbol;
    }

    public String getLabel() { return label; }
    public String getSymbol() { return symbol; }
}
