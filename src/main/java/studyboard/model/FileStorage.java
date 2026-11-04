package studyboard.model;

import java.io.*;             // IOException
import java.nio.charset.*;    // StandardCharsets
import java.nio.file.*;       // Path, Files, StandardCopyOption...
import java.time.*;           // LocalDate, DateTimeException
import java.util.*;           // List, ArrayList

/**
 * Guarda y carga el repositorio en un fichero de texto.
 *
 * Formato (un registro por línea, campos separados por TAB):
 *   STUDYBOARD 1
 *   SUBJECT  id  name
 *   TASK     id  title  notes  subjectId  STATUS  PRIORITY  yyyy-MM-dd  hours
 *
 * Los tabuladores, saltos de línea y barras dentro de un texto se "escapan"
 * (se escriben \t, \n, \\) para que un título nunca rompa la estructura del fichero.
 */
public class FileStorage {
    private static final String SEPARATOR = "\t";          // separador de campos
    private static final String HEADER = "STUDYBOARD 1";   // nombre del formato + versión

    private final Path file; // ruta del fichero de datos

    public FileStorage(Path file) {
        this.file = file;
    }

    /** Ubicación por defecto: ~/.studyboard/data.txt (sirve desde cualquier carpeta). */
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".studyboard", "data.txt");
    }

    public Path getFile() { return file; }

    public boolean exists() { return Files.exists(file); }

    // ------------------------------------------------------------------ guardar

    public void save(TaskRepository repo) throws IOException {
        List<String> lines = new ArrayList<>(); // todas las líneas del fichero
        lines.add(HEADER);

        for (Subject s : repo.getSubjects()) {
            lines.add(String.join(SEPARATOR, "SUBJECT", String.valueOf(s.getId()), escape(s.getName())));
        }
        for (Task t : repo.getTasks()) {
            lines.add(String.join(SEPARATOR, "TASK",
                    String.valueOf(t.getId()), escape(t.getTitle()), escape(t.getNotes()),
                    String.valueOf(t.getSubjectId()), t.getStatus().name(), t.getPriority().name(),
                    t.getDeadline().toString(),               // formato ISO: 2026-11-04
                    String.valueOf(t.getEstimatedHours())));
        }

        Files.createDirectories(file.toAbsolutePath().getParent()); // crea la carpeta si no existe

        // Guardado seguro: escribimos un fichero temporal y luego sustituimos el real.
        // Si el programa se cae a mitad, el fichero antiguo sigue intacto.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); // plan B
        }
    }

    // ------------------------------------------------------------------ cargar

    /**
     * Rellena el repositorio (vacío) desde el fichero.
     * Lanza IOException si no se puede leer O si el contenido es inválido
     * (el mensaje dice qué línea está mal).
     */
    public void load(TaskRepository repo) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) {
            throw new IOException("Unknown file format.");
        }

        for (int i = 1; i < lines.size(); i++) { // empezamos en 1: la línea 0 es la cabecera
            String line = lines.get(i);
            if (line.isBlank()) continue; // ignoramos líneas vacías
            String[] parts = line.split(SEPARATOR, -1); // -1 conserva campos vacíos (notas vacías)
            try {
                switch (parts[0]) { // parts[0] = tipo de registro
                    case "SUBJECT" -> {
                        if (parts.length != 3) throw new IllegalArgumentException("a SUBJECT needs 3 fields");
                        repo.restoreSubject(Integer.parseInt(parts[1]), unescape(parts[2]));
                    }
                    case "TASK" -> repo.restoreTask(parseTask(parts));
                    default -> throw new IllegalArgumentException("unknown record '" + parts[0] + "'");
                }
            } catch (IllegalArgumentException | DateTimeException e) {
                // NumberFormatException es un tipo de IllegalArgumentException, así que cae aquí también
                throw new IOException("Line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
    }

    /** Renombra un fichero dañado para no perderlo ni sobrescribirlo. Devuelve la nueva ruta. */
    public Path backupBrokenFile() throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        Files.move(file, backup);
        return backup;
    }

    // ------------------------------------------------------------------ auxiliares

    /** Convierte los 9 campos de una línea TASK en un objeto Task. */
    private Task parseTask(String[] p) {
        if (p.length != 9) throw new IllegalArgumentException("a TASK needs 9 fields");
        double hours = Double.parseDouble(p[8]);
        if (!Double.isFinite(hours)) throw new IllegalArgumentException("invalid number of hours");
        // valueOf lanza IllegalArgumentException si el nombre no existe (así se rechaza el fichero)
        Task task = new Task(Integer.parseInt(p[1]), unescape(p[2]), Integer.parseInt(p[4]),
                Priority.valueOf(p[6]), LocalDate.parse(p[7]), hours);
        task.setNotes(unescape(p[3]));
        task.setStatus(Status.valueOf(p[5]));
        return task;
    }

    /** Texto -> texto seguro para un campo. La barra \ se sustituye PRIMERO. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** Lo contrario de escape(): recorre el texto carácter a carácter. */
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(); // constructor de texto eficiente
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i); // miramos el carácter tras la barra
                switch (next) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    default -> out.append(next); // "\\" vuelve a ser "\"
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
