package studyboard.view.swing;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import studyboard.model.*;
import studyboard.model.SortOrder; // javax.swing also has a SortOrder, this says we mean ours
import studyboard.view.*;

/** Modal window for search, filter and sort. It starts with the current values and tells the Controller what to apply. */
class FilterDialog extends JDialog {

    FilterDialog(JFrame owner, ViewListener listener, BoardOptions options, Map<Integer, String> subjectNames) {
        super(owner, "Search, filter and sort", true); //use the board's current filter and sort order as the initial values
        TaskFilter current = options.filter(); //the current filter, to be used as the initial values of the form

        // Position 0 of the subject / priority lists means "no restriction", so real values start at 1
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> subjectLabels = new ArrayList<>();
        subjectLabels.add("All subjects"); // position 0 = no restriction

        subjectLabels.addAll(subjectNames.values());
        List<String> priorityLabels = new ArrayList<>();
        priorityLabels.add("Any priority"); // position 0 = no restriction

        for (Priority p : Priority.values()) {
            priorityLabels.add(p.getLabel()); // position 1 = LOW, 2 = MEDIUM, 3 = HIGH
        }
        List<String> sortLabels = new ArrayList<>();
        for (SortOrder s : SortOrder.values()) {
            sortLabels.add(s.getLabel()); // position 0 = DATE, 1 = SUBJECT, 2 = PRIORITY, 3 = STATUS
        }

        JTextField text = new JTextField(current.text(), 24); // the search text, 24 columns wide
        JComboBox<String> subject = new JComboBox<>(subjectLabels.toArray(new String[0])); // the subject filter, with "All subjects" at position 0
        JComboBox<String> priority = new JComboBox<>(priorityLabels.toArray(new String[0])); // the priority filter, with "Any priority" at position 0
        JCheckBox hideDone = new JCheckBox("Hide done tasks", current.hideDone()); // the "hide done tasks" checkbox, initially checked if the current filter hides done tasks
        JComboBox<String> sort = new JComboBox<>(sortLabels.toArray(new String[0])); // the sort order, with the current sort order selected

        if (current.subjectId() != null) {
            int index = subjectIds.indexOf(current.subjectId()); // find the index of the current subject ID in the list of subject IDs
            if (index >= 0) subject.setSelectedIndex(index + 1); // +1 because of "All subjects"
        }
        if (current.priority() != null) {
            priority.setSelectedIndex(current.priority().ordinal() + 1); // +1 because of "Any priority"
        }
        sort.setSelectedIndex(options.sort().ordinal()); // select the current sort order

        JPanel form = new JPanel(new GridBagLayout()); //the form with the search text, subject, priority, hide done tasks, and sort order
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        Ui.addRow(form, 0, "Search text", text);
        Ui.addRow(form, 1, "Subject", subject);
        Ui.addRow(form, 2, "Priority", priority);
        Ui.addRow(form, 3, "Done tasks", hideDone);
        Ui.addRow(form, 4, "Sort by", sort);

        JButton apply = new JButton("Apply");
        JButton clear = new JButton("Clear filter");
        JButton cancel = new JButton("Cancel");
        apply.addActionListener(e -> { // apply the filter and sort order, then close the dialog
            Integer subjectId = subject.getSelectedIndex() == 0 ? null : subjectIds.get(subject.getSelectedIndex() - 1);// for subject, position 0 = no restriction, so we return null, otherwise we return the subject ID at position -1 because of "All subjects"
            Priority chosen = priority.getSelectedIndex() == 0 ? null : Priority.values()[priority.getSelectedIndex() - 1];
            listener.onChangeView(new TaskFilter(text.getText(), subjectId, chosen, hideDone.isSelected()),
                    SortOrder.values()[sort.getSelectedIndex()]); //onChangeView() is called with the new filter and sort order, then the dialog is closed
            dispose();
        });
        clear.addActionListener(e -> {
            listener.onChangeView(TaskFilter.NONE, SortOrder.values()[sort.getSelectedIndex()]); // clear the filter (TaskFilter.NONE) but keep the current sort order, then close the dialog
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); // the buttons at the bottom right: Cancel, Clear filter, Apply
        buttons.add(cancel);
        buttons.add(clear);
        buttons.add(apply);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(apply); // pressing Enter applies the filter and sort order
        Ui.closeOnEsc(this); // pressing Esc closes the dialog
        Ui.helpOnF1(getRootPane(), this, "Help - Filter", HelpTexts.FILTER);
        pack(); // size the dialog to fit its contents
        setLocationRelativeTo(owner); // owner is the main window, so the dialog is centered on the main window
        setResizable(false); // the dialog is not resizable, because it is small and fixed-size
    }
}
