package studyboard.model;

/** A course, for example "HCI" or "Mathematics". */
public class Subject {
    private final int id;     // final = cannot change after creation
    private String name;

    public Subject(int id, String name) {
        this.id = id;
        setName(name); // reuse the setter so validation is in one place
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        // Error prevention: never allow an empty subject name.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Subject name cannot be empty.");
        }
        this.name = name.trim();
    }

    @Override
    public String toString() {
        return name;
    }
}