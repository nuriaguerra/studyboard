package studyboard.view.swing;

import java.awt.*;          // Component, GridBagConstraints, Font
import java.awt.event.*;    // ActionEvent
import java.time.*;         // LocalDate
import java.time.format.*;  // DateTimeFormatter, DateTimeParseException, ResolverStyle
import java.util.Map;
import javax.swing.*;
import studyboard.model.*;

/** Small helpers shared by all the Swing windows (dates, messages, keyboard shortcuts). Nobody creates objects of it. */
final class Ui {
    private Ui() { }

    static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");
    static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // STRICT + "uuuu": 31.02.2026 is rejected instead of silently changed to 28.02.2026 (same rule as in text mode)
    static final DateTimeFormatter DATE_INPUT = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** One line per task: priority symbol + LATE + title + (subject, date). Colour is never the only signal. */
    static String taskLine(Task t, Map<Integer, String> names, LocalDate today) {
        String symbol = t.getPriority().getSymbol().isBlank() ? "" : t.getPriority().getSymbol() + " ";
        String late = t.isOverdue(today) ? "LATE " : "";
        return symbol + late + t.getTitle() + "  (" + names.getOrDefault(t.getSubjectId(), "?")
                + ", " + t.getDeadline().format(SHORT_DATE) + ")";
    }

    /** Text -> date, with a message that says exactly what is wrong. */
    static LocalDate parseDate(String text, LocalDate today) {
        try {
            return LocalDate.parse(text.trim(), DATE_INPUT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The deadline must be a real date written as DD.MM.YYYY, for example "
                    + today.plusDays(7).format(LONG_DATE) + ".");
        }
    }

    /** Text -> hours. It accepts "2.5" and "2,5". */
    static double parseHours(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The estimate must be a number of hours, for example 2.5.");
        }
    }

    /** Adds "label | field" as one row of a GridBagLayout form. */
    static void addRow(JPanel panel, int row, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 4);   // space around each component
        c.anchor = GridBagConstraints.WEST;  // left aligned
        c.gridx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL; // the field grows to the right
        c.weightx = 1;
        panel.add(field, c);
    }

    /** Registers a keyboard shortcut for a whole window. keyStroke examples: "ctrl N", "F1", "ESCAPE", "DELETE". */
    static void bindKey(JRootPane root, String keyStroke, Runnable action) {
        String id = "action-" + keyStroke; // any unique name works
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), id); // key -> name
        root.getActionMap().put(id, new AbstractAction() {                                              // name -> code
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** Esc closes the dialog (same behaviour in every window). */
    static void closeOnEsc(JDialog dialog) {
        bindKey(dialog.getRootPane(), "ESCAPE", dialog::dispose);
    }

    /** F1 opens the help of this window. */
    static void helpOnF1(JRootPane root, Component parent, String title, String text) {
        bindKey(root, "F1", () -> showText(parent, title, text));
    }

    /** Read-only text in a small window (help, task details). Monospaced font keeps the columns aligned. */
    static void showText(Component parent, String title, String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setOpaque(false);
        JOptionPane.showMessageDialog(parent, area, title, JOptionPane.PLAIN_MESSAGE);
    }

    static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    /** Yes/No question (error prevention). "No" is the first button and the default one: Enter alone does NOT confirm. */
    static boolean confirm(Component parent, String title, String message) {
        Object[] options = {"No", "Yes"};
        int answer = JOptionPane.showOptionDialog(parent, message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]); // options[0] = initial (focused) button
        return answer == 1; // 1 = "Yes"; closing the window gives -1 = no
    }
}
