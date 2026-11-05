package studyboard.model;

import java.io.*; // IOException
import java.nio.charset.*; // StandardCharsets
import java.nio.file.*; // Path, Files, StandardCopyOption...
import java.time.*; // LocalDate, DateTimeException
import java.util.*; // List, ArrayList

/**
 * Saves and loads the repository as a plain text file.
 *
 * File format (one record per line, fields separated by a TAB):
 *   STUDYBOARD 1
 *   SUBJECT  id  name
 *   TASK     id  title  notes  subjectId  STATUS  PRIORITY  yyyy-MM-dd  hours
 */
public class FileStorage {
    private static final String SEPARATOR = "\t"; // the field separator (a TAB character)
    private static final String HEADER = "STUDYBOARD 1"; // format name + version, first line of the file

    private final Path file; // path of the data file (a Path is Java's way to write a file location)

    public FileStorage(Path file) {
        this.file = file; // the file where the data is saved and loaded
    }

    /** Default location: ~/.studyboard/data.txt (it works from any working directory). */
    public static Path defaultPath() {
        // user.home is the home folder of the user (e.g. /home/nuria in my case)
        return Path.of(System.getProperty("user.home"), ".studyboard", "data.txt");
    }

    public Path getFile() { return file; } // returns the path of the data file (for example to show it in a message)

    public boolean exists() { return Files.exists(file); } // false on the very first run

    ////// save

    // "throws IOException" = this method can fail (disk full, no permission...) and the caller must handle it
    public void save(TaskRepository repo) throws IOException {
        List<String> lines = new ArrayList<>(); // all the lines of the file, in memory
        lines.add(HEADER); // first line: the file format name and version

        for (Subject s : repo.getSubjects()) { // we save the subjects first, so they exist when we read the tasks
            // String.join glues the pieces with a TAB between them
            lines.add(String.join(SEPARATOR, "SUBJECT", String.valueOf(s.getId()), escape(s.getName())));
            //it is show like this: SUBJECT  1   Math
        }
        for (Task t : repo.getTasks()) { //for each task, we save its data in a line
            lines.add(String.join(SEPARATOR, "TASK",
                    String.valueOf(t.getId()), escape(t.getTitle()), escape(t.getNotes()),
                    String.valueOf(t.getSubjectId()), t.getStatus().name(), t.getPriority().name(),
                    t.getDeadline().toString(), // ISO format: 2026-11-04
                    String.valueOf(t.getEstimatedHours()))); // name() gives the enum name, e.g. "IN_PROGRESS"
        }

        Files.createDirectories(file.toAbsolutePath().getParent()); //creates the folder if it does not exist

        // Safe saving: we write a temporary file first and then replace the real one.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp"); // same folder, name + ".tmp"
        Files.write(temp, lines, StandardCharsets.UTF_8); // UTF-8 keeps accents and special letters
        try {
            // ATOMIC_MOVE = the replacement happens in one step, nobody can see a half-written file
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); // plan B for systems without atomic moves
        }
    }

    ////// load

    /**
     * Fills the (empty) repository from the file.
     * Throws IOException if the file cannot be read OR if its content is invalid
     */
    public void load(TaskRepository repo) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) { //if the file is empty or the first line is not the expected header
            throw new IOException("Unknown file format.");
        }

        for (int i = 1; i < lines.size(); i++) { //we start at 1: line 0 is the header
            String line = lines.get(i);
            if (line.isBlank()) continue; //ignore empty lines ("continue" jumps to the next line of the loop)
            String[] parts = line.split(SEPARATOR, -1); // -1 keeps empty fields (for example empty notes)
            try {
                switch (parts[0]) { // parts[0] = the type of record
                    case "SUBJECT" -> {
                        if (parts.length != 3) throw new IllegalArgumentException("a SUBJECT needs 3 fields"); // we check the number of fields before reading them
                        repo.restoreSubject(Integer.parseInt(parts[1]), unescape(parts[2])); // unescape() converts the text back to its original form (for example it turns "\t" into a TAB character)
                    }
                    case "TASK" -> repo.restoreTask(parseTask(parts)); // parseTask() reads the 9 fields of a TASK line and returns a Task object
                    default -> throw new IllegalArgumentException("unknown record '" + parts[0] + "'");
                }
            } catch (IllegalArgumentException | DateTimeException e) {
                // NumberFormatException is a kind of IllegalArgumentException, so it lands here too.
                // We wrap the problem in an IOException that says which line is wrong (i + 1 because humans count from 1).
                throw new IOException("Line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
    }

    /** Renames a damaged file so it is not overwritten. Returns the new path. */
    public Path backupBrokenFile() throws IOException {
        // The current time in milliseconds
        //resolveSibling is used to create a new path in the same directory as the original file, with a modified name.
        Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        Files.move(file, backup); //move the original file to the new backup path
        return backup;
    }

    //////helpers

    /** Converts the 9 fields of a TASK line into a Task object. */
    private Task parseTask(String[] p) {
        // Check the number of fields before reading them
        if (p.length != 9) throw new IllegalArgumentException("a TASK needs 9 fields");

        double hours = Double.parseDouble(p[8]); // throws NumberFormatException if it is not a number the hours
        if (!Double.isFinite(hours)) throw new IllegalArgumentException("invalid number of hours"); // if it is NaN or infinite, we reject it

        // valueOf throws IllegalArgumentException if the name does not exist (that is how a bad file is rejected)
        Task task = new Task(Integer.parseInt(p[1]), unescape(p[2]), Integer.parseInt(p[4]),
                Priority.valueOf(p[6]), LocalDate.parse(p[7]), hours);

        task.setNotes(unescape(p[3])); // notes and status are not constructor parameters, unescape() converts the text back to its original form
        task.setStatus(Status.valueOf(p[5]));
        return task;
    }

    /** Text -> safe text for one field. The backslash must be replaced first (if not, we would escape our own escapes). */
    private static String escape(String s) {
        return s.replace("\\", "\\\\")  // "\" -> "\\"
                .replace("\t", "\\t")   // TAB character -> the two characters \t
                .replace("\n", "\\n")   // new line -> \n
                .replace("\r", "\\r");  // carriage return -> \r
    }

    /** The opposite of escape(): it reads the text character by character. */
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(); // builds the result character by character
        for (int i = 0; i < s.length(); i++) { // we read the string character by character
            char c = s.charAt(i); // the current character
            if (c == '\\' && i + 1 < s.length()) { // if it is a backslash and there is a next character
                char next = s.charAt(++i); // ++i moves forward first and then reads: we look at the character after the backslash
                switch (next) { // we check what the next character is
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    default -> out.append(next); // "\\" becomes "\"
                }
            } else {
                out.append(c); // normal character is copied as it is
            }
        }
        return out.toString();
    }
}
