package studyboard.model;

/** A course, for example "HCI" or "Math". Only data + validation, nothing about the screen. */
public class Subject {
    private final int id;   // unique identifier; final = it cannot change after the object is created
    private String name;    // name (this one can change, because a subject can be renamed)

    public Subject(int id, String name) { // constructor: runs when we write new Subject(...)
        this.id = id;
        setName(name); // we reuse the setter, so the validation rules live in ONE single place
    }

    // Getters: other classes can read the data but cannot touch the fields directly
    public int getId() { return id; }
    public String getName() { return name; }

    /** Changes the name, rejecting names that are empty or too long. */
    public void setName(String name) {
        if (name == null || name.isBlank()) { // isBlank = empty or only spaces
            // throw = stop here and report an error. The View catches it and shows the message.
            throw new IllegalArgumentException("Subject name cannot be empty.");
        }
        if (name.trim().length() > 30) { // trim() removes spaces at the start and the end
            throw new IllegalArgumentException("Subject name is too long (maximum 30 characters).");
        }
        this.name = name.trim(); // we store the clean version (without the extra spaces)
    }

    @Override // replaces the toString() that every Java object already has
    public String toString() { return name; } // so printing a Subject shows its name
}
