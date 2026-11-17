package studyboard.view.swing;

import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;                 // java.awt and java.util both have a List: we mean java.util's
import java.util.function.Consumer;
import javax.swing.*;
import studyboard.model.*;

/**
 * Modal window (the user must close it before going back to the board) to create or edit a task.
 * It does NOT change any data: it builds a TaskData (which validates everything) and hands it to 'onSave'.
 */
class TaskFormDialog extends JDialog {

    /** existing == null means "new task"; otherwise "edit this task". */
    TaskFormDialog(JFrame owner, Task existing, Map<Integer, String> subjectNames, LocalDate today,
                   Consumer<TaskData> onSave) {
        super(owner, existing == null ? "New task" : "Edit task", true); // true = modal
        boolean editing = existing != null;

        // The combo box shows names; subjectIds has the SAME order, so index -> id
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> priorityLabels = new ArrayList<>();
        for (Priority p : Priority.values()) {
            priorityLabels.add(p.getLabel());
        }

        JTextField title = new JTextField(editing ? existing.getTitle() : "", 30);
        JComboBox<String> subject = new JComboBox<>(subjectNames.values().toArray(new String[0]));
        JComboBox<String> priority = new JComboBox<>(priorityLabels.toArray(new String[0]));
        JTextField deadline = new JTextField((editing ? existing.getDeadline() : today.plusDays(7)).format(Ui.LONG_DATE), 10);
        JTextField hours = new JTextField(editing ? String.valueOf(existing.getEstimatedHours()) : "1.0", 6);
        JTextArea notes = new JTextArea(editing ? existing.getNotes() : "", 4, 30);
        notes.setLineWrap(true);

        if (editing) { // preselect the current values
            int index = subjectIds.indexOf(existing.getSubjectId());
            if (index >= 0) subject.setSelectedIndex(index);
            priority.setSelectedIndex(existing.getPriority().ordinal());
        } else {
            priority.setSelectedIndex(Priority.MEDIUM.ordinal());
        }

        JPanel form = new JPanel(new GridBagLayout());
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
                // Creating TaskData VALIDATES everything; a wrong value throws IllegalArgumentException
                TaskData data = new TaskData(title.getText(), notes.getText(),
                        subjectIds.get(subject.getSelectedIndex()),
                        Priority.values()[priority.getSelectedIndex()],
                        Ui.parseDate(deadline.getText(), today), Ui.parseHours(hours.getText()));
                dispose();           // close the form...
                onSave.accept(data); // ...and give the data to the View, which asks the Controller
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
        pack();                          // size = what the components need
        setLocationRelativeTo(owner);    // centred over the main window
        setResizable(false);
    }
}
