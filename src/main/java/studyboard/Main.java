package studyboard;

import com.formdev.flatlaf.FlatLightLaf;  // modern look for Swing windows
import javax.swing.SwingUtilities;        // lets us run code on the Swing thread
import javax.swing.UIManager;git status  q

import studyboard.controller.*;
import studyboard.model.*;
import studyboard.view.*;
import studyboard.view.lanterna.*;        // LanternaView (text mode, Project 1)
import studyboard.view.swing.*;           // SwingView (graphical, Project 2)

/**
 * ENTRY POINT. Only the View changes between projects; Model and Controller are the same.
 * Usage:  java -jar target/studyboard.jar              (graphical window, Project 2)
 *         java -jar target/studyboard.jar --text       (text mode in the terminal, Project 1)
 *         java -jar target/studyboard.jar --emulator   (text mode inside an emulated window)
 */
public class Main {
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "--gui"; // no argument = graphical

        TaskRepository repository = new TaskRepository();                  // MODEL: data in memory
        FileStorage storage = new FileStorage(FileStorage.defaultPath());  // MODEL: data on disk

        if (mode.equals("--text") || mode.equals("--emulator")) {
            // Project 1: text mode. Lanterna runs on the main thread.
            View view = new LanternaView(mode.equals("--emulator"));
            new Controller(repository, view, storage).start();
        } else {
            // Project 2: Swing. EVERYTHING that touches Swing must run on the "Event Dispatch Thread" (EDT),
            // the thread that draws windows and receives clicks. invokeLater sends this block to it.
            // So the Controller also lives on the EDT, and every click arrives there too.
            SwingUtilities.invokeLater(() -> {
                UIManager.put("Component.arc", 12);
                UIManager.put("Button.arc", 12);
                UIManager.put("TextComponent.arc", 12);
                UIManager.put("defaultFont", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14));

                FlatLightLaf.setup();                       // FIRST: the look must be set before creating any component
                View view = new SwingView();                // VIEW: the new graphical screen
                new Controller(repository, view, storage).start();
            });
        }
    }
}
