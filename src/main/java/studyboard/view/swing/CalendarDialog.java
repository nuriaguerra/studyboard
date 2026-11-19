package studyboard.view.swing;

import java.awt.*; // for the layout managers, colors, fonts, etc.
import java.time.*; // for LocalDate and YearMonth
import java.time.format.*; // for TextStyle
import java.util.*; // for List, Map, Comparator, Locale
import java.util.List;
import javax.swing.*; // for the Swing components
import studyboard.model.*;

/** Modal monthly calendar. It receives all the tasks: the board filter does not hide deadlines. */
class CalendarDialog extends JDialog {
    private final List<Task> tasks;
    private final Map<Integer, String> names;
    private final LocalDate today;
    private YearMonth month;
    private final JLabel title = new JLabel("", SwingConstants.CENTER); // the month and year at the top center
    private final JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4)); //7 columns = 7 days
    private final JTextArea deadlines = new JTextArea(6, 40); // the list of deadlines for the month, below the grid

    CalendarDialog(JFrame owner, List<Task> tasks, Map<Integer, String> names, LocalDate today) {
        super(owner, "Calendar", true);
        this.tasks = tasks;
        this.names = names;
        this.today = today;
        this.month = YearMonth.from(today);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f)); // bigger 16f and bold
        JButton prev = new JButton("←"); // the previous month
        JButton next = new JButton("→"); // the next month
        JButton now = new JButton("Today"); // the current month (today's month)
        prev.addActionListener(e -> change(-1));
        next.addActionListener(e -> change(+1));
        now.addActionListener(e -> { month = YearMonth.from(today); draw(); });

        JPanel right = new JPanel(new FlowLayout()); // the next and today buttons, at the top right
        right.add(next);
        right.add(now);
        JPanel top = new JPanel(new BorderLayout()); // the previous button, the title, and the next/today buttons
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12)); // top padding 10, left/right 12, bottom 4
        top.add(prev, BorderLayout.WEST);
        top.add(title, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        grid.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        deadlines.setEditable(false); // the list of deadlines is read-only
        JLabel legend = new JLabel("Boxed bold day = today     *n = n tasks pending     +n = all done");
        legend.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
        JPanel bottom = new JPanel(new BorderLayout()); // the list of deadlines and the legend, below the grid
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        bottom.add(new JScrollPane(deadlines), BorderLayout.CENTER);
        bottom.add(legend, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        draw(); // build the grid and the list for the current month
        Ui.closeOnEsc(this); // pressing Esc closes the dialog
        Ui.bindKey(getRootPane(), "LEFT", () -> change(-1));
        Ui.bindKey(getRootPane(), "RIGHT", () -> change(+1));
        Ui.bindKey(getRootPane(), "T", () -> { month = YearMonth.from(today); draw(); });
        Ui.helpOnF1(getRootPane(), this, "Help - Calendar", HelpTexts.CALENDAR);
        pack(); // size the dialog to fit its contents
        setLocationRelativeTo(owner);
    }

    private void change(int delta) {
        month = month.plusMonths(delta); // go to the previous or next month
        draw();
    }

    /** Rebuilds the grid and the list for the month stored in 'month'. */
    private void draw() {
        title.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear()); // month name and year at the top
        grid.removeAll();
        for (String d : new String[] {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}) { // the day names at the top of the grid
            JLabel h = new JLabel(d, SwingConstants.CENTER);
            h.setFont(h.getFont().deriveFont(Font.BOLD));
            grid.add(h);
        }
        int cells = 0; // the number of cells added to the grid (including the day names)
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) { // empty cells before day 1 (Monday = 1)
            grid.add(new JLabel(""));
            cells++;
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) { // for each day of the month
            LocalDate date = month.atDay(day);
            int total = 0, pending = 0; // the number of tasks with this deadline, and the number of pending tasks
            for (Task t : tasks) {
                if (t.getDeadline().equals(date)) {
                    total++; // this task has a deadline on this day
                    if (t.getStatus() != Status.DONE) pending++;
                }
            }
            String mark = total == 0 ? "&nbsp;" : (pending > 0 ? "*" : "+") + total; // *2 pending, +1 done
            boolean isToday = date.equals(today);
            JLabel cell = new JLabel("<html><div style='text-align:center'>" + (isToday ? "<b>" + day + "</b>" : day)
                    + "<br>" + mark + "</div></html>", SwingConstants.CENTER); // the day number and the mark, centered

            // Today: a box around the day
            cell.setBorder(isToday ? BorderFactory.createLineBorder(UIManager.getColor("Label.foreground"), 2)
                    : BorderFactory.createEmptyBorder(2, 2, 2, 2));

            if (total > 0) cell.setForeground(pending > 0 ? new Color(0xD1242F) : new Color(0x1A7F37)); // red for pending, green for done
            grid.add(cell);
            cells++;
        }
        while (cells < 42) { // always 6 weeks, so the window never changes size between months
            grid.add(new JLabel(""));
            cells++;
        }

        // Build the list of deadlines for the month
        StringBuilder text = new StringBuilder();
        tasks.stream()
                .filter(t -> YearMonth.from(t.getDeadline()).equals(month)) // only tasks with a deadline in this month
                .sorted(Comparator.comparing(Task::getDeadline)) // sort by deadline
                .forEach(t -> text.append(Ui.taskLine(t, names, today)) // add the task line to the text
                        .append(t.getStatus() == Status.DONE ? "  [done]" : "").append('\n'));
        deadlines.setText(text.length() == 0 ? "No deadlines this month." : text.toString());
        deadlines.setCaretPosition(0);
        grid.revalidate();
        grid.repaint();
    }
}
