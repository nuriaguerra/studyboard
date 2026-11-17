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
 * Window to create, rename and delete subjects. It is NOT modal: it stays open while the Controller refreshes it
 * (showSubjects() is called again after every change and only calls refill()).
 */
class SubjectsDialog extends JDialog {
    private final ViewListener listener;
    private final DefaultListModel<String> model = new DefaultListModel<>(); // the rows
    private final JList<String> list = new JList<>(model);
    private final JLabel status = new JLabel(" ");   // messages from the Controller
    private List<Subject> shown = new ArrayList<>(); // same order as the rows
    private Map<Integer, Integer> counts = Map.of(); // subjectId -> number of tasks

    SubjectsDialog(JFrame owner, ViewListener listener) {
        super(owner, "Subjects", false); // false = not modal
        this.listener = listener;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14)); // monospaced: the task counts line up
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
        add.addActionListener(e -> newSubject());
        rename.addActionListener(e -> renameSelected());
        delete.addActionListener(e -> deleteSelected());
        undo.addActionListener(e -> { listener.onUndo(); listener.onShowSubjects(); }); // undo, then refresh this list
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add); buttons.add(rename); buttons.add(delete); buttons.add(undo); buttons.add(close);
        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.NORTH);
        status.setBorder(BorderFactory.createEmptyBorder(2, 12, 8, 12));
        south.add(status, BorderLayout.SOUTH);

        JLabel intro = new JLabel("Your subjects (number of tasks on the right)");
        intro.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        add(intro, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        Ui.closeOnEsc(this);
        Ui.helpOnF1(getRootPane(), this, "Help - Subjects", HelpTexts.SUBJECTS);
        setSize(520, 380);
        setLocationRelativeTo(owner);
    }

    /** Refills the list (the cursor stays near where it was). */
    void refill(List<Subject> subjects, Map<Integer, Integer> taskCounts) {
        shown = new ArrayList<>(subjects); // a copy: the Model's list may change
        counts = taskCounts;
        int previous = list.getSelectedIndex();
        model.clear();
        for (Subject s : shown) {
            model.addElement(String.format("%-30s %3d task(s)", s.getName(), counts.getOrDefault(s.getId(), 0)));
        }
        if (!shown.isEmpty()) {
            list.setSelectedIndex(Math.max(0, Math.min(previous, shown.size() - 1)));
        }
    }

    void setStatus(String message) {
        status.setText(message);
    }

    private Subject selected() {
        int index = list.getSelectedIndex();
        return (index >= 0 && index < shown.size()) ? shown.get(index) : null;
    }

    private void newSubject() {
        askName("New subject", "", name -> {
            listener.onAddSubject(name);
            list.setSelectedIndex(shown.size() - 1); // the Controller already refilled the list: select the new (last) one
        });
    }

    private void renameSelected() {
        Subject s = selected();
        if (s == null) {
            Ui.error(this, "Rename", "Select a subject first.");
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
        int count = counts.getOrDefault(s.getId(), 0);
        String text = count == 0 ? "Delete the subject \"" + s.getName() + "\"?"
                : "Delete the subject \"" + s.getName() + "\" and its " + count + " task(s)?\nThis can be undone with Undo.";
        if (Ui.confirm(this, "Delete subject", text)) {
            listener.onDeleteSubject(s.getId());
        }
    }

    /** Asks for a name and runs 'action'. If the Model rejects it, the message is shown and we ask again with the same text. */
    private void askName(String title, String initial, Consumer<String> action) {
        String text = initial;
        while (true) {
            Object answer = JOptionPane.showInputDialog(this, "Subject name:", title,
                    JOptionPane.PLAIN_MESSAGE, null, null, text);
            if (answer == null) return; // Cancel or Esc
            text = answer.toString();
            try {
                action.accept(text);
                return;
            } catch (IllegalArgumentException e) {
                Ui.error(this, "Please check the name", e.getMessage());
            }
        }
    }
}
