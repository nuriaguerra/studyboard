package studyboard.view.swing;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import studyboard.model.*;
import studyboard.view.*;

/**
 * Window to create, rename and delete subjects. It stays open while the Controller refreshes it
 * (showSubjects() is called again after every change and only calls refill()).
 */
class SubjectsDialog extends JDialog {
    private final ViewListener listener; // the Controller
    private final DefaultListModel<String> model = new DefaultListModel<>(); // the rows
    private final JList<String> list = new JList<>(model);
    private final JLabel status = new JLabel(" "); //messages from the Controller
    private List<Subject> shown = new ArrayList<>(); // the subjects currently shown in the list (the same order as the rows in 'model')
    private Map<Integer, Integer> counts = Map.of(); // subjectId -> number of tasks

    SubjectsDialog(JFrame owner, ViewListener listener) {
        super(owner, "Subjects", false); //the Controller keeps it open
        this.listener = listener;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); //for closing with the X button (the Controller will reopen it if needed)

        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14)); //monospaced font so the numbers align
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); //only one subject can be selected at a time

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) renameSelected(); // double-click = rename
            }
        });

        JButton add = new JButton("New");
        JButton rename = new JButton("Rename");
        JButton delete = new JButton("Delete");
        JButton undo = new JButton("Undo");
        JButton close = new JButton("Close");
        add.addActionListener(e -> newSubject()); //action listeners for the buttons
        rename.addActionListener(e -> renameSelected());
        delete.addActionListener(e -> deleteSelected());
        undo.addActionListener(e -> { listener.onUndo(); listener.onShowSubjects(); }); // undo, then refresh this list
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT)); //the buttons are on the left
        buttons.add(add); buttons.add(rename); buttons.add(delete); buttons.add(undo); buttons.add(close);
        JPanel south = new JPanel(new BorderLayout()); //the buttons are on the top, the status message is below them
        south.add(buttons, BorderLayout.NORTH);
        status.setBorder(BorderFactory.createEmptyBorder(2, 12, 8, 12));
        south.add(status, BorderLayout.SOUTH);

        JLabel intro = new JLabel("Your subjects (number of tasks on the right)"); //the text above the list
        intro.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        JScrollPane scroll = new JScrollPane(list); //the list is scrollable
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        add(intro, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        Ui.closeOnEsc(this); //close the window when Esc is pressed
        Ui.helpOnF1(getRootPane(), this, "Help - Subjects", HelpTexts.SUBJECTS); //show the help text when F1 is pressed
        setSize(520, 380);
        setLocationRelativeTo(owner);
    }

    //Refills the list (the cursor stays near where it was).
    void refill(List<Subject> subjects, Map<Integer, Integer> taskCounts) {
        shown = new ArrayList<>(subjects); //the Controller may have filtered the subjects, so we need to remember which ones are shown
        counts = taskCounts; //the Controller may have changed the number of tasks, so we need to remember the new counts
        int previous = list.getSelectedIndex(); //previous selection index
        model.clear(); //clear the list
        for (Subject s : shown) {
            model.addElement(String.format("%-30s %3d task(s)", s.getName(), counts.getOrDefault(s.getId(), 0))); //add the subject name and the number of tasks to the list
        }
        if (!shown.isEmpty()) { //if there are subjects, select the previous one
            list.setSelectedIndex(Math.max(0, Math.min(previous, shown.size() - 1)));
        }
    }

    void setStatus(String message) {
        status.setText(message);
    }

    private Subject selected() {
        int index = list.getSelectedIndex(); //the index of the selected row in the list
        return (index >= 0 && index < shown.size()) ? shown.get(index) : null; //return the corresponding Subject object, or null if nothing is selected
    }

    private void newSubject() {
        askName("New subject", "", name -> {
            listener.onAddSubject(name);
            list.setSelectedIndex(shown.size() - 1); //select the new subject (it is added at the end of the list)
        });
    }

    private void renameSelected() {
        Subject s = selected(); //the subject that is currently selected in the list
        if (s == null) {
            Ui.error(this, "Rename", "Select a subject first."); //if nothing is selected, show an error message
            return;
        }
        askName("Rename subject", s.getName(), name -> listener.onRenameSubject(s.getId(), name));
    }

    private void deleteSelected() {
        Subject s = selected();
        if (s == null) {
            Ui.error(this, "Delete", "Select a subject first.");
            return;
        }
        int count = counts.getOrDefault(s.getId(), 0); //the number of tasks in the selected subject
        String text = count == 0 ? "Delete the subject \"" + s.getName() + "\"?" //if there are no tasks, just ask if the subject should be deleted
                : "Delete the subject \"" + s.getName() + "\" and its " + count + " task(s)?\nThis can be undone with Undo."; //if there are tasks, warn the user that they will be deleted too
        if (Ui.confirm(this, "Delete subject", text)) {
            listener.onDeleteSubject(s.getId());
        }
    }

    //Asks for a name and runs 'action'. If the Model rejects it, the message is shown and we ask again with the same text.
    private void askName(String title, String initial, Consumer<String> action) {
        String text = initial;
        while (true) {
            Object answer = JOptionPane.showInputDialog(this, "Subject name:", title,
                    JOptionPane.PLAIN_MESSAGE, null, null, text); //show a dialog with a text field
            if (answer == null) return; // Cancel or Esc
            text = answer.toString(); //the text entered by the user
            try {
                action.accept(text); //run the action (the Controller will call refill() if it succeeds)
                return;
            } catch (IllegalArgumentException e) {
                Ui.error(this, "Please check the name", e.getMessage());
            }
        }
    }
}
