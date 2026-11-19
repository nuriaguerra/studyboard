package studyboard;

import com.formdev.flatlaf.FlatLightLaf;  // modern look for Swing windows
import javax.swing.SwingUtilities;        // lets us run code on the Swing thread
import javax.swing.UIManager;

import studyboard.controller.*;
import studyboard.model.*;
import studyboard.view.*;
import studyboard.view.lanterna.*;        // LanternaView (text mode, Project 1)
import studyboard.view.swing.*;           // SwingView (graphical, Project 2)

/**
 * Only the View changes between projects; Model and Controller are the same.
 * Usage:  java -jar target/studyboard.jar (graphical window, Project 2)
 *         java -jar target/studyboard.jar --text  (text mode in the terminal, Project 1)
 *         java -jar target/studyboard.jar --emulator   (text mode inside an emulated window)
 */
public class Main {
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "--gui"; // no argument = graphical
        //if you dont use any argument, it will default to graphical mode for the project 2 (gui), if you use --text it will run the project 1 in the terminal, 
        //and if you use --emulator it will run the project 1 in a window that emulates a terminal

        TaskRepository repository = new TaskRepository();  // model, data in memory
        FileStorage storage = new FileStorage(FileStorage.defaultPath());  // model, data on disk

        if (mode.equals("--text") || mode.equals("--emulator")) {
            // If you use --text or --emulator, it will run Project 1 (text mode). Lanterna runs on the main thread.
            View view = new LanternaView(mode.equals("--emulator"));
            new Controller(repository, view, storage).start();

        } else {
            // Project 2: Swing. Everything must run on the "Event Dispatch Thread" (EDT), the thread that draws windows and receives clicks.

            //invokeLater its a safer thread to run the code, the controller also lives on the EDT, and every click arrives there too.
            SwingUtilities.invokeLater(() -> {
                //UIManager.put its a way to customize the look of the Swing components
                UIManager.put("Component.arc", 12); //round corners 12 pixels for all components
                UIManager.put("Button.arc", 12); //round corners 12 pixels for buttons
                UIManager.put("TextComponent.arc", 12); //round corners 12 pixels for text components
                UIManager.put("defaultFont", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14)); //set the default font to SansSerif, plain, size 14

                FlatLightLaf.setup(); //the light look must be set before creating any component
                View view = new SwingView(); //the new graphical screen
                new Controller(repository, view, storage).start(); //the controller set with the new view
            });
        }
    }
}
