package studyboard;

import studyboard.controller.Controller;
import studyboard.model.SampleData;
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

        TaskRepository repository = new TaskRepository(); // Model
        SampleData.load(repository);                      // example data
        View view = new LanternaView(swing);              // View (could be any View implementation)
        new Controller(repository, view).start();         // Controller runs the app
    }
}