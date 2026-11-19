package studyboard.view.swing;

import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;  // java.awt and java.util both have a List, we mean java.util's
import java.util.function.Consumer;
import javax.swing.*;
import studyboard.model.*;

/**
 * the user must close it before going back to the board to create or edit a task.
 * It does not change any data: it builds a TaskData (which validates everything) and hands it to 'onSave'.
 */
class TaskFormDialog extends JDialog {

    //existing == null means "new task", otherwise "edit this task".
    TaskFormDialog(JFrame owner, Task existing, Map<Integer, String> subjectNames, LocalDate today, Consumer<TaskData> onSave) {
        super(owner, existing == null ? "New task" : "Edit task", true); // true = modal, so the user must close it before going back to the board
        boolean editing = existing != null; // true if we are editing an existing task, false if we are creating a new one

        //subjectIds has the same order, so index -> id
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> priorityLabels = new ArrayList<>();
        for (Priority p : Priority.values()) {
            priorityLabels.add(p.getLabel()); //shows the label, not the symbol
        }

        JTextField title = new JTextField(editing ? existing.getTitle() : "", 30); // 30 columns = about 30 characters
        JComboBox<String> subject = new JComboBox<>(subjectNames.values().toArray(new String[0])); // shows the subject names, not the ids
        JComboBox<String> priority = new JComboBox<>(priorityLabels.toArray(new String[0])); // shows the priority labels, not the symbols
        JTextField deadline = new JTextField((editing ? existing.getDeadline() : today.plusDays(7)).format(Ui.LONG_DATE), 10); //+7 days is the default deadline for new tasks
        JTextField hours = new JTextField(editing ? String.valueOf(existing.getEstimatedHours()) : "1.0", 6); // default 1 hour for new tasks, 6 columns = about 6 characters
        JTextArea notes = new JTextArea(editing ? existing.getNotes() : "", 4, 30);
        notes.setLineWrap(true); // wrap long lines, so the user doesn't have to scroll horizontally

        if (editing) { //preselect the current values
            int index = subjectIds.indexOf(existing.getSubjectId());
            if (index >= 0) subject.setSelectedIndex(index); // the subject might have been deleted, so check if it is still in the list
            priority.setSelectedIndex(existing.getPriority().ordinal());
        } else {
            priority.setSelectedIndex(Priority.MEDIUM.ordinal());
        }

        JPanel form = new JPanel(new GridBagLayout()); // the form uses a grid layout with labels on the left and fields on the right
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        Ui.addRow(form, 0, "Title", title);
        Ui.addRow(form, 1, "Subject", subject);
        Ui.addRow(form, 2, "Priority", priority);
        Ui.addRow(form, 3, "Deadline (DD.MM.YYYY)", deadline);
        Ui.addRow(form, 4, "Estimate (hours)", hours);
        Ui.addRow(form, 5, "Notes (optional)", new JScrollPane(notes));

        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(e -> {
            try {
                // Creating TaskData validates everything; a wrong value throws IllegalArgumentException
                TaskData data = new TaskData(title.getText(), notes.getText(),
                        subjectIds.get(subject.getSelectedIndex()),
                        Priority.values()[priority.getSelectedIndex()],
                        Ui.parseDate(deadline.getText(), today), Ui.parseHours(hours.getText()));
                dispose();  //close the form
                onSave.accept(data); //give the data to the View, which asks the Controller
            } catch (IllegalArgumentException ex) {
                // The form stays open so the user can fix the problem without typing everything again
                Ui.error(this, "Please check the form", ex.getMessage());
            }
        });
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(save); // Enter presses Save
        Ui.closeOnEsc(this);
        Ui.helpOnF1(getRootPane(), this, "Help - Task form", HelpTexts.TASK_FORM);
        pack();  // pack() makes the window just big enough to fit the form and buttons
        setLocationRelativeTo(owner); // centred over the main window
        setResizable(false);
    }
}
