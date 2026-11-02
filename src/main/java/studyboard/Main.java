package studyboard;

import studyboard.controller.Controller;
import studyboard.model.FileStorage;
import studyboard.model.TaskRepository;
import studyboard.view.View;
import studyboard.view.lanterna.LanternaView;

/**
 * Entry point: creates Model, View and Controller and connects them.
 * Usage:  java -jar target/studyboard.jar            (real terminal)
 *         java -jar target/studyboard.jar --swing    (emulator window)
 */
public class Main {
    public static void main(String[] args) {
        boolean swing = args.length > 0 && args[0].equals("--swing");

        TaskRepository repository = new TaskRepository();            // Model (data in memory)
        FileStorage storage = new FileStorage(FileStorage.defaultPath()); // Model (data on disk)
        View view = new LanternaView(swing);                         // View
        new Controller(repository, view, storage).start();           // Controller runs the app
    }
}