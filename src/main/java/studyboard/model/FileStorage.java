package studyboard.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads the repository as a plain text file.
 *
 * File format (one record per line, fields separated by a TAB):
 *   STUDYBOARD 1
 *   SUBJECT  id  name
 *   TASK     id  title  notes  subjectId  STATUS  PRIORITY  yyyy-MM-dd  hours
 *
 * Tabs, new lines and backslashes inside texts are "escaped" (written as \t, \n, \\)
 * so a title can never break the structure of the file.
 */
public class FileStorage {
    private static final String SEPARATOR = "\t";
    private static final String HEADER = "STUDYBOARD 1"; // format name + version

    private final Path file;

    public FileStorage(Path file) {
        this.file = file;
    }

    /** Default location: ~/.studyboard/data.txt (works from any working directory). */
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".studyboard", "data.txt");
    }

    public Path getFile() {
        return file;
    }

    public boolean exists() {
        return Files.exists(file);
    }

    // ------------------------------------------------------------------ save

    public void save(TaskRepository repo) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (Subject s : repo.getSubjects()) {
            lines.add(String.join(SEPARATOR, "SUBJECT",
                    String.valueOf(s.getId()), escape(s.getName())));
        }
        for (Task t : repo.getTasks()) {
            lines.add(String.join(SEPARATOR, "TASK",
                    String.valueOf(t.getId()), escape(t.getTitle()), escape(t.getNotes()),
                    String.valueOf(t.getSubjectId()), t.getStatus().name(), t.getPriority().name(),
                    t.getDeadline().toString(),            // ISO format: 2026-11-04
                    String.valueOf(t.getEstimatedHours())));
        }

        // Make sure the folder exists (first run).
        Files.createDirectories(file.toAbsolutePath().getParent());

        // Safe saving: write a temporary file first and then replace the real one.
        // If the program crashes while writing, the old data file is still intact.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); // fallback
        }
    }

    // ------------------------------------------------------------------ load

    /**
     * Fills the (empty) repository from the file.
     * Throws IOException if the file cannot be read OR if its content is invalid.
     * The message says which line is wrong.
     */
    public void load(TaskRepository repo) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) {
            throw new IOException("Unknown file format.");
        }

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue; // ignore empty lines
            }
            String[] parts = line.split(SEPARATOR, -1); // -1 keeps empty fields (e.g. empty notes)
            try {
                switch (parts[0]) {
                    case "SUBJECT" -> {
                        if (parts.length != 3) {
                            throw new IllegalArgumentException("a SUBJECT needs 3 fields");
                        }
                        repo.restoreSubject(Integer.parseInt(parts[1]), unescape(parts[2]));
                    }
                    case "TASK" -> repo.restoreTask(parseTask(parts));
                    default -> throw new IllegalArgumentException("unknown record '" + parts[0] + "'");
                }
            } catch (IllegalArgumentException | DateTimeException e) {
                // NumberFormatException is a kind of IllegalArgumentException, so it lands here too.
                throw new IOException("Line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
    }

    /** Renames a damaged file so it is not overwritten. Returns the new path. */
    public Path backupBrokenFile() throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        Files.move(file, backup);
        return backup;
    }

    // ------------------------------------------------------------------ helpers

    private Task parseTask(String[] p) {
        if (p.length != 9) {
            throw new IllegalArgumentException("a TASK needs 9 fields");
        }
        double hours = Double.parseDouble(p[8]);
        if (!Double.isFinite(hours)) {
            throw new IllegalArgumentException("invalid number of hours");
        }
        // Enum.valueOf throws IllegalArgumentException for unknown names (good: file is rejected).
        Task task = new Task(Integer.parseInt(p[1]), unescape(p[2]), Integer.parseInt(p[4]),
                Priority.valueOf(p[6]), LocalDate.parse(p[7]), hours);
        task.setNotes(unescape(p[3]));
        task.setStatus(Status.valueOf(p[5]));
        return task;
    }

    /** Text -> safe text for one field. The backslash must be replaced FIRST. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** Reverse of escape(): reads the text character by character. */
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i); // look at the character after the backslash
                switch (next) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    default -> out.append(next); // "\\" becomes "\"
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}