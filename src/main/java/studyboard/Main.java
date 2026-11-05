package studyboard; // folder/package where this class lives

import studyboard.controller.*;    // Controller
import studyboard.model.*;         // TaskRepository, FileStorage
import studyboard.view.*;          // View (the interface)
import studyboard.view.lanterna.*; // LanternaView (the text mode screen)

/**
 * ENTRY POINT: Java always starts a program in the method called main.
 * Here we only create the 3 pieces of MVC and connect them. No logic lives in this class.
 * Usage:  java -jar target/studyboard.jar          (real terminal)
 *         java -jar target/studyboard.jar --swing  (emulated window)
 */
public class Main {
    public static void main(String[] args) {
        // Did the user write --swing? (args = the words written after the program name)
        // args.length > 0 is checked first, so we never read args[0] when there are no arguments.
        boolean swing = args.length > 0 && args[0].equals("--swing");

        TaskRepository repository = new TaskRepository();                  // MODEL: data in memory
        FileStorage storage = new FileStorage(FileStorage.defaultPath());  // MODEL: data on disk
        // The variable type is View (the interface), not LanternaView: this is what lets us replace
        // the screen in Project 2 without touching anything else.
        View view = new LanternaView(swing);                               // VIEW: what the user sees
        new Controller(repository, view, storage).start();                 // CONTROLLER: connects everything and starts
    }
}
