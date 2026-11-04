package studyboard.model;

/** Una asignatura, por ejemplo "HCI" o "Math". Solo datos + validación, nada de pantalla. */
public class Subject {
    private final int id;   // identificador único; final = no cambia tras crearse
    private String name;    // nombre (sí se puede renombrar)

    public Subject(int id, String name) {
        this.id = id;
        setName(name); // reutilizamos el setter para que la validación esté en UN solo sitio
    }

    public int getId() { return id; }
    public String getName() { return name; }

    /** Cambia el nombre, rechazando nombres vacíos o demasiado largos. */
    public void setName(String name) {
        if (name == null || name.isBlank()) { // isBlank = vacío o solo espacios
            throw new IllegalArgumentException("Subject name cannot be empty.");
        }
        if (name.trim().length() > 30) {
            throw new IllegalArgumentException("Subject name is too long (maximum 30 characters).");
        }
        this.name = name.trim(); // trim quita espacios al principio y al final
    }

    @Override // sobrescribe el método de Java que convierte el objeto en texto
    public String toString() { return name; }
}
