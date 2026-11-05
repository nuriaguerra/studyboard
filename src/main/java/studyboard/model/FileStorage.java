package studyboard.model;

import java.io.*;             // IOException
import java.nio.charset.*;    // StandardCharsets
import java.nio.file.*;       // Path, Files, StandardCopyOption...
import java.time.*;           // LocalDate, DateTimeException
import java.util.*;           // List, ArrayList

/**
 * Saves and loads the repository as a plain text file.
 *
 * File format (one record per line, fields separated by a TAB):
 *   STUDYBOARD 1
 *   SUBJECT  id  name
 *   TASK     id  title  notes  subjectId  STATUS  PRIORITY  yyyy-MM-dd  hours
 *
 * Tabs, new lines and backslashes inside the texts are "escaped" (written as \t, \n, \\)
 * so a title can never break the structure of the file.
 */
public class FileStorage {
    private static final String SEPARATOR = "\t";          // the field separator (a TAB character)
    private static final String HEADER = "STUDYBOARD 1";   // format name + version, first line of the file

    private final Path file; // path of the data file (a Path is Java's way to write a file location)

    public FileStorage(Path file) {
        this.file = file;
    }

    /** Default location: ~/.studyboard/data.txt (it works from any working directory). */
    public static Path defaultPath() {
        // user.home is the home folder of the user, for example /home/nuriaguerra
        return Path.of(System.getProperty("user.home"), ".studyboard", "data.txt");
    }

    public Path getFile() { return file; }

    public boolean exists() { return Files.exists(file); } // false on the very first run

    // ------------------------------------------------------------------ save

    // "throws IOException" = this method can fail (disk full, no permission...) and the caller must handle it
    public void save(TaskRepository repo) throws IOException {
        List<String> lines = new ArrayList<>(); // all the lines of the file, in memory
        lines.add(HEADER);

        for (Subject s : repo.getSubjects()) {
            // String.join glues the pieces with a TAB between them
            lines.add(String.join(SEPARATOR, "SUBJECT", String.valueOf(s.getId()), escape(s.getName())));
        }
        for (Task t : repo.getTasks()) {
            lines.add(String.join(SEPARATOR, "TASK",
                    String.valueOf(t.getId()), escape(t.getTitle()), escape(t.getNotes()),
                    String.valueOf(t.getSubjectId()), t.getStatus().name(), t.getPriority().name(),
                    t.getDeadline().toString(),               // ISO format: 2026-11-04
                    String.valueOf(t.getEstimatedHours())));  // name() gives the enum name, e.g. "IN_PROGRESS"
        }

        Files.createDirectories(file.toAbsolutePath().getParent()); // creates the folder if it does not exist

        // Safe saving: we write a temporary file first and then replace the real one.
        // If the program crashes while writing, the old data file is still intact.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp"); // same folder, name + ".tmp"
        Files.write(temp, lines, StandardCharsets.UTF_8);             // UTF-8 keeps accents and special letters
        try {
            // ATOMIC_MOVE = the replacement happens in one step, nobody can see a half-written file
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); // plan B for systems without atomic moves
        }
    }

    // ------------------------------------------------------------------ load

    /**
     * Fills the (empty) repository from the file.
     * Throws IOException if the file cannot be read OR if its content is invalid
     * (the message says which line is wrong).
     */
    public void load(TaskRepository repo) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) {
            throw new IOException("Unknown file format.");
        }

        for (int i = 1; i < lines.size(); i++) { // we start at 1: line 0 is the header
            String line = lines.get(i);
            if (line.isBlank()) continue; // ignore empty lines ("continue" jumps to the next line of the loop)
            String[] parts = line.split(SEPARATOR, -1); // -1 keeps empty fields (for example empty notes)
            try {
                switch (parts[0]) { // parts[0] = the type of record
                    case "SUBJECT" -> {
                        if (parts.length != 3) throw new IllegalArgumentException("a SUBJECT needs 3 fields");
                        repo.restoreSubject(Integer.parseInt(parts[1]), unescape(parts[2]));
                    }
                    case "TASK" -> repo.restoreTask(parseTask(parts));
                    default -> throw new IllegalArgumentException("unknown record '" + parts[0] + "'");
                }
            } catch (IllegalArgumentException | DateTimeException e) {
                // NumberFormatException is a kind of IllegalArgumentException, so it lands here too.
                // We wrap the problem in an IOException that says WHICH line is wrong (i + 1 because humans count from 1).
                throw new IOException("Line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
    }

    /** Renames a damaged file so it is not overwritten. Returns the new path. */
    public Path backupBrokenFile() throws IOException {
        // The current time in milliseconds makes the new name unique, e.g. data.txt.broken-1762200000000
        Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        Files.move(file, backup);
        return backup;
    }

    // ------------------------------------------------------------------ helpers

    /** Converts the 9 fields of a TASK line into a Task object. */
    private Task parseTask(String[] p) {
        if (p.length != 9) throw new IllegalArgumentException("a TASK needs 9 fields");
        double hours = Double.parseDouble(p[8]); // throws NumberFormatException if it is not a number
        if (!Double.isFinite(hours)) throw new IllegalArgumentException("invalid number of hours");
        // valueOf throws IllegalArgumentException if the name does not exist (that is how a bad file is rejected)
        Task task = new Task(Integer.parseInt(p[1]), unescape(p[2]), Integer.parseInt(p[4]),
                Priority.valueOf(p[6]), LocalDate.parse(p[7]), hours);
        task.setNotes(unescape(p[3])); // notes and status are not constructor parameters, so we set them afterwards
        task.setStatus(Status.valueOf(p[5]));
        return task;
    }

    /** Text -> safe text for one field. The backslash must be replaced FIRST (if not, we would escape our own escapes). */
    private static String escape(String s) {
        return s.replace("\\", "\\\\")  // "\" -> "\\"
                .replace("\t", "\\t")   // TAB character -> the two characters \t
                .replace("\n", "\\n")   // new line -> \n
                .replace("\r", "\\r");  // carriage return -> \r
    }

    /** The opposite of escape(): it reads the text character by character. */
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(); // an efficient way to build a text piece by piece
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i); // ++i moves forward first and then reads: we look at the character after the backslash
                switch (next) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    default -> out.append(next); // "\\" becomes "\"
                }
            } else {
                out.append(c); // normal character: copy it as it is
            }
        }
        return out.toString();
    }
}
