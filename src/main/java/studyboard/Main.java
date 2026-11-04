package studyboard; // carpeta/paquete donde vive esta clase

import studyboard.controller.*; // Controller
import studyboard.model.*;      // TaskRepository, FileStorage
import studyboard.view.*;       // View (la interfaz)
import studyboard.view.lanterna.*; // LanternaView (la pantalla de texto)

/**
 * PUNTO DE ENTRADA: Java empieza siempre por el método main.
 * Aquí solo se crean las 3 piezas de MVC y se conectan.
 * Uso:  java -jar target/studyboard.jar          (terminal real)
 *       java -jar target/studyboard.jar --swing  (ventana emulada)
 */
public class Main {
    public static void main(String[] args) {
        // ¿El usuario escribió --swing? (args = palabras escritas tras el nombre del programa)
        boolean swing = args.length > 0 && args[0].equals("--swing");

        TaskRepository repository = new TaskRepository();                  // MODEL: datos en memoria
        FileStorage storage = new FileStorage(FileStorage.defaultPath());  // MODEL: datos en disco
        View view = new LanternaView(swing);                               // VIEW: lo que se ve
        new Controller(repository, view, storage).start();                 // CONTROLLER: une todo y arranca
    }
}
