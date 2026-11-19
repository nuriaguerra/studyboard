package studyboard.view.swing;

import java.awt.*;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.*;
import studyboard.model.*;

//Modal window "Today", chose the best suggestion, using the reasons, and the next ones.
class TodayDialog extends JDialog {

    /** 'ranked' comes sorted from the Controller. 'mover' asks the Controller to move a task (the View never changes data). */
    TodayDialog(JFrame owner, List<Recommendation> ranked, Map<Integer, String> names, LocalDate today, BiConsumer<Task, Status> mover) {
        super(owner, "Today", true);

        JPanel content = new JPanel(); // the content of the window, which is scrollable if it doesn't fit on the screen
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS)); // vertical layout, one component per line
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        JLabel header = new JLabel("Today: " + today.format(Ui.LONG_DATE));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f)); // bigger and bold
        put(content, header); // the 'put' method aligns the component to the left, which is what we want for a vertical layout
        content.add(Box.createVerticalStrut(10)); // 10 px of empty space

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton first = null; // the main button (default one)

        if (ranked.isEmpty()) {
            put(content, new JLabel("Nothing pending. All your tasks are done!")); // the list is empty, so there is nothing to do
        } else {
            Recommendation next = ranked.get(0); // the first suggestion is the best one, so we show it in a card with the reasons and a button to move it
            Task task = next.task();

            // The suggestion, in a box with a titled border
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS)); // vertical layout, one component per line
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Next up"), // titled border
                    BorderFactory.createEmptyBorder(6, 10, 8, 10)));

            JLabel title = new JLabel(task.getTitle());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f)); // bigger and bold
            put(card, title);
            put(card, new JLabel(names.getOrDefault(task.getSubjectId(), "?") // the subject name (or ? if the subject was deleted)
                    + "  |  due " + task.getDeadline().format(Ui.LONG_DATE) // the deadline in dd.MM.YYYY format
                    + "  |  " + task.getEstimatedHours() + " h  |  " + task.getPriority().getLabel() + " priority")); // the estimated hours and the priority label (not the symbol)
            
            card.add(Box.createVerticalStrut(8)); // 8 px of empty space before the reasons
            JLabel why = new JLabel("Why now?");
            why.setFont(why.getFont().deriveFont(Font.BOLD)); // bold
            put(card, why);
            for (String reason : next.reasons()) { // one line per reason (this is the "explanation")
                put(card, new JLabel("  - " + reason));
            }
            put(card, new JLabel("Score: " + next.score())); // the score is shown at the bottom, so the user can see how good the suggestion is
            put(content, card);

            if (ranked.size() > 1) { // the next 4 suggestions
                content.add(Box.createVerticalStrut(10)); // 10 px of empty space before the next suggestions
                JLabel also = new JLabel("Also coming up");
                also.setFont(also.getFont().deriveFont(Font.BOLD));
                put(content, also);
                for (int i = 1; i < Math.min(5, ranked.size()); i++) { // show up to 4 more suggestions (the first one is already shown above)
                    put(content, new JLabel("  " + Ui.taskLine(ranked.get(i).task(), names, today)));
                }
            }

            boolean todo = task.getStatus() == Status.TODO; // if the task is TODO, the button will start it (move to IN_PROGRESS), otherwise it will mark it as done (move to DONE)
            first = new JButton(todo ? "Start it" : "Mark as done");
            first.addActionListener(e -> { // the button moves the task to the next status (the View never changes data, it asks the Controller to do it)
                mover.accept(task, todo ? Status.IN_PROGRESS : Status.DONE);
                dispose(); //close the window, because the task is no longer pending
            });
            buttons.add(first);
        }
        JButton open = new JButton("Open board");
        open.addActionListener(e -> dispose()); //close the window, so the user can see the board and do whatever they want
        buttons.add(open);

        content.add(Box.createVerticalStrut(10)); // 10 px of empty space before the buttons
        put(content, buttons);
        add(content);

        getRootPane().setDefaultButton(first != null ? first : open); // if there is a first button, it is the default one (Enter presses it), otherwise the "Open board" button is the default one
        Ui.closeOnEsc(this);
        Ui.helpOnF1(getRootPane(), this, "Help - Today", HelpTexts.TODAY);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false); // the window is not resizable, because it is a dialog with a fixed layout
    }

    /** Adds a component to a BoxLayout panel, aligned to the left. */
    private static void put(JPanel panel, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT); // align the component to the left, which is what we want for a vertical layout
        panel.add(c); // add the component to the panel
    }
}
