package studyboard.view.swing;

import java.awt.*;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.*;
import studyboard.model.*;

/** Modal window "Today": the best suggestion, WHY (the reasons), and the next ones. */
class TodayDialog extends JDialog {

    /** 'ranked' comes sorted from the Controller. 'mover' asks the Controller to move a task (the View never changes data). */
    TodayDialog(JFrame owner, List<Recommendation> ranked, Map<Integer, String> names, LocalDate today,
                BiConsumer<Task, Status> mover) {
        super(owner, "Today", true);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS)); // components stacked vertically
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        JLabel header = new JLabel("Today: " + today.format(Ui.LONG_DATE));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        put(content, header);
        content.add(Box.createVerticalStrut(10)); // 10 px of empty space

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton first = null; // the main button (default one)

        if (ranked.isEmpty()) {
            put(content, new JLabel("Nothing pending. All your tasks are done!"));
        } else {
            Recommendation next = ranked.get(0);
            Task task = next.task();

            // The suggestion, in a box with a titled border
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Next up"),
                    BorderFactory.createEmptyBorder(6, 10, 8, 10)));
            JLabel title = new JLabel(task.getTitle());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
            put(card, title);
            put(card, new JLabel(names.getOrDefault(task.getSubjectId(), "?")
                    + "  |  due " + task.getDeadline().format(Ui.LONG_DATE)
                    + "  |  " + task.getEstimatedHours() + " h  |  " + task.getPriority().getLabel() + " priority"));
            card.add(Box.createVerticalStrut(8));
            JLabel why = new JLabel("Why now?");
            why.setFont(why.getFont().deriveFont(Font.BOLD));
            put(card, why);
            for (String reason : next.reasons()) {           // one line per reason (this is the "explanation")
                put(card, new JLabel("  - " + reason));
            }
            put(card, new JLabel("Score: " + next.score()));
            put(content, card);

            if (ranked.size() > 1) { // the next 4 suggestions
                content.add(Box.createVerticalStrut(10));
                JLabel also = new JLabel("Also coming up");
                also.setFont(also.getFont().deriveFont(Font.BOLD));
                put(content, also);
                for (int i = 1; i < Math.min(5, ranked.size()); i++) {
                    put(content, new JLabel("  " + Ui.taskLine(ranked.get(i).task(), names, today)));
                }
            }

            boolean todo = task.getStatus() == Status.TODO;
            first = new JButton(todo ? "Start it" : "Mark as done");
            first.addActionListener(e -> {
                mover.accept(task, todo ? Status.IN_PROGRESS : Status.DONE);
                dispose();
            });
            buttons.add(first);
        }
        JButton open = new JButton("Open board");
        open.addActionListener(e -> dispose());
        buttons.add(open);

        content.add(Box.createVerticalStrut(10));
        put(content, buttons);
        add(content);

        getRootPane().setDefaultButton(first != null ? first : open);
        Ui.closeOnEsc(this);
        Ui.helpOnF1(getRootPane(), this, "Help - Today", HelpTexts.TODAY);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /** Adds a component to a BoxLayout panel, aligned to the left. */
    private static void put(JPanel panel, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
    }
}
