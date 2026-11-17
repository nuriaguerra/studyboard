package studyboard.view.swing;

import java.awt.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import studyboard.model.*;

/** Modal monthly calendar. It receives ALL the tasks: the board filter does not hide deadlines. */
class CalendarDialog extends JDialog {
    private final List<Task> tasks;
    private final Map<Integer, String> names;
    private final LocalDate today;
    private YearMonth month;                                   // month shown now
    private final JLabel title = new JLabel("", SwingConstants.CENTER);
    private final JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4)); // 7 columns = 7 days
    private final JTextArea deadlines = new JTextArea(6, 40);

    CalendarDialog(JFrame owner, List<Task> tasks, Map<Integer, String> names, LocalDate today) {
        super(owner, "Calendar", true);
        this.tasks = tasks;
        this.names = names;
        this.today = today;
        this.month = YearMonth.from(today);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JButton prev = new JButton("←");
        JButton next = new JButton("→");
        JButton now = new JButton("Today");
        prev.addActionListener(e -> change(-1));
        next.addActionListener(e -> change(+1));
        now.addActionListener(e -> { month = YearMonth.from(today); draw(); });

        JPanel right = new JPanel(new FlowLayout());
        right.add(next);
        right.add(now);
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        top.add(prev, BorderLayout.WEST);
        top.add(title, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        grid.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        deadlines.setEditable(false);
        JLabel legend = new JLabel("Boxed bold day = today     *n = n tasks pending     +n = all done");
        legend.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        bottom.add(new JScrollPane(deadlines), BorderLayout.CENTER);
        bottom.add(legend, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        draw();
        Ui.closeOnEsc(this);
        Ui.bindKey(getRootPane(), "LEFT", () -> change(-1));
        Ui.bindKey(getRootPane(), "RIGHT", () -> change(+1));
        Ui.bindKey(getRootPane(), "T", () -> { month = YearMonth.from(today); draw(); });
        Ui.helpOnF1(getRootPane(), this, "Help - Calendar", HelpTexts.CALENDAR);
        pack();
        setLocationRelativeTo(owner);
    }

    private void change(int delta) {
        month = month.plusMonths(delta);
        draw();
    }

    /** Rebuilds the grid and the list for the month stored in 'month'. */
    private void draw() {
        title.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear());
        grid.removeAll();
        for (String d : new String[] {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}) {
            JLabel h = new JLabel(d, SwingConstants.CENTER);
            h.setFont(h.getFont().deriveFont(Font.BOLD));
            grid.add(h);
        }
        int cells = 0;
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) { // empty cells before day 1 (Monday = 1)
            grid.add(new JLabel(""));
            cells++;
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            int total = 0, pending = 0;
            for (Task t : tasks) {
                if (t.getDeadline().equals(date)) {
                    total++;
                    if (t.getStatus() != Status.DONE) pending++;
                }
            }
            String mark = total == 0 ? "&nbsp;" : (pending > 0 ? "*" : "+") + total; // *2 pending, +1 done
            boolean isToday = date.equals(today);
            JLabel cell = new JLabel("<html><div style='text-align:center'>" + (isToday ? "<b>" + day + "</b>" : day)
                    + "<br>" + mark + "</div></html>", SwingConstants.CENTER);
            // Today: a box around the day (not only colour). Days with tasks: red = pending, green = all done.
            cell.setBorder(isToday ? BorderFactory.createLineBorder(UIManager.getColor("Label.foreground"), 2)
                    : BorderFactory.createEmptyBorder(2, 2, 2, 2));
            if (total > 0) cell.setForeground(pending > 0 ? new Color(0xD1242F) : new Color(0x1A7F37));
            grid.add(cell);
            cells++;
        }
        while (cells < 42) { // always 6 weeks, so the window never changes size between months
            grid.add(new JLabel(""));
            cells++;
        }

        StringBuilder text = new StringBuilder();
        tasks.stream()
                .filter(t -> YearMonth.from(t.getDeadline()).equals(month))
                .sorted(Comparator.comparing(Task::getDeadline))
                .forEach(t -> text.append(Ui.taskLine(t, names, today))
                        .append(t.getStatus() == Status.DONE ? "  [done]" : "").append('\n'));
        deadlines.setText(text.length() == 0 ? "No deadlines this month." : text.toString());
        deadlines.setCaretPosition(0);
        grid.revalidate();
        grid.repaint();
    }
}
