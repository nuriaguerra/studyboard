package studyboard.view.swing;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import studyboard.model.*;
import studyboard.model.SortOrder; // javax.swing also has a SortOrder: this says we mean ours
import studyboard.view.*;

/** Modal window for search, filter and sort. It starts with the current values and tells the Controller what to apply. */
class FilterDialog extends JDialog {

    FilterDialog(JFrame owner, ViewListener listener, BoardOptions options, Map<Integer, String> subjectNames) {
        super(owner, "Search, filter and sort", true);
        TaskFilter current = options.filter();

        // Position 0 of the subject / priority lists means "no restriction", so real values start at 1
        List<Integer> subjectIds = new ArrayList<>(subjectNames.keySet());
        List<String> subjectLabels = new ArrayList<>();
        subjectLabels.add("All subjects");
        subjectLabels.addAll(subjectNames.values());
        List<String> priorityLabels = new ArrayList<>();
        priorityLabels.add("Any priority");
        for (Priority p : Priority.values()) {
            priorityLabels.add(p.getLabel());
        }
        List<String> sortLabels = new ArrayList<>();
        for (SortOrder s : SortOrder.values()) {
            sortLabels.add(s.getLabel());
        }

        JTextField text = new JTextField(current.text(), 24);
        JComboBox<String> subject = new JComboBox<>(subjectLabels.toArray(new String[0]));
        JComboBox<String> priority = new JComboBox<>(priorityLabels.toArray(new String[0]));
        JCheckBox hideDone = new JCheckBox("Hide done tasks", current.hideDone());
        JComboBox<String> sort = new JComboBox<>(sortLabels.toArray(new String[0]));

        if (current.subjectId() != null) {
            int index = subjectIds.indexOf(current.subjectId());
            if (index >= 0) subject.setSelectedIndex(index + 1); // +1 because of "All subjects"
        }
        if (current.priority() != null) {
            priority.setSelectedIndex(current.priority().ordinal() + 1);
        }
        sort.setSelectedIndex(options.sort().ordinal());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        Ui.addRow(form, 0, "Search text", text);
        Ui.addRow(form, 1, "Subject", subject);
        Ui.addRow(form, 2, "Priority", priority);
        Ui.addRow(form, 3, "Done tasks", hideDone);
        Ui.addRow(form, 4, "Sort by", sort);

        JButton apply = new JButton("Apply");
        JButton clear = new JButton("Clear filter");
        JButton cancel = new JButton("Cancel");
        apply.addActionListener(e -> {
            Integer subjectId = subject.getSelectedIndex() == 0 ? null : subjectIds.get(subject.getSelectedIndex() - 1);
            Priority chosen = priority.getSelectedIndex() == 0 ? null : Priority.values()[priority.getSelectedIndex() - 1];
            listener.onChangeView(new TaskFilter(text.getText(), subjectId, chosen, hideDone.isSelected()),
                    SortOrder.values()[sort.getSelectedIndex()]);
            dispose();
        });
        clear.addActionListener(e -> {
            listener.onChangeView(TaskFilter.NONE, SortOrder.values()[sort.getSelectedIndex()]);
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(clear);
        buttons.add(apply);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(apply);
        Ui.closeOnEsc(this);
        Ui.helpOnF1(getRootPane(), this, "Help - Filter", HelpTexts.FILTER);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }
}
