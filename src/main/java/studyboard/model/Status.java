package studyboard.model;

/** Las 3 columnas del Kanban. Un enum es una lista FIJA de valores permitidos. */
public enum Status {
    // Cada valor lleva un texto bonito entre paréntesis (se pasa al constructor de abajo)
    TODO("To do"),
    IN_PROGRESS("In progress"),
    DONE("Done");

    private final String label; // texto que ve el usuario (final = no cambia nunca)

    Status(String label) {      // constructor del enum: guarda el texto
        this.label = label;
    }

    public String getLabel() {  // getter: permite leer el texto desde fuera
        return label;
    }
}
