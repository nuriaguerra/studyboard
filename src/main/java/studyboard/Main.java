package studyboard;

import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

/**
 * Temporary entry point: only checks that Lanterna works.
 * Later this class will create the Model, View and Controller and start the app.
 *
 * Usage:  java -jar target/studyboard.jar            (real terminal, the normal mode)
 *         java -jar target/studyboard.jar --swing    (separate emulator window)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // Did the user ask for the Swing emulator window?
        boolean swing = args.length > 0 && args[0].equals("--swing");

        // The factory creates the "terminal" Lanterna draws on.
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setForceTextTerminal(!swing);   // true = use the real terminal
        factory.setPreferTerminalEmulator(swing); // true = open a Swing window instead
        Terminal terminal = factory.createTerminal();

        // A Screen is a buffer on top of the terminal; startScreen() switches to full-screen mode.
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();

        // The GUI manages windows and keyboard focus.
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        BasicWindow window = new BasicWindow("StudyBoard");

        // A Panel holds components (labels, buttons...). Default layout: vertical.
        Panel panel = new Panel();
        panel.addComponent(new Label("Hello StudyBoard! Lanterna works."));
        panel.addComponent(new Button("Exit", window::close)); // closes the window when pressed
        window.setComponent(panel);

        // Shows the window and blocks until it is closed.
        gui.addWindowAndWait(window);

        // Restore the normal terminal. Always do this at the end.
        screen.stopScreen();
    }
}