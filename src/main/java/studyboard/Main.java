package studyboard; // folder/package where this class lives

import studyboard.controller.*; // Controller
import studyboard.model.*; // TaskRepository, FileStorage
import studyboard.view.*; // View (the interface)
import studyboard.view.lanterna.*; // LanternaView (the text mode screen)

/**
 * Java always starts a program in the method called main.
 * Usage:  java -jar target/studyboard.jar          (real terminal)
 *         java -jar target/studyboard.jar --swing  (emulated window)
 */
public class Main {
    public static void main(String[] args) {
        //If the user write --swing (args = the words written after the program name)
        //args.length > 0 is checked first, so we never read args[0] when there are no arguments.
        boolean swing = args.length > 0 && args[0].equals("--swing");

        TaskRepository repository = new TaskRepository(); // model, data in memory
        FileStorage storage = new FileStorage(FileStorage.defaultPath());  //model, data on disk
        // The variable type is View (the interface), not LanternaView
        View view = new LanternaView(swing);   // view, what the user sees
        new Controller(repository, view, storage).start(); // controller, connects everything and starts
    }
}
