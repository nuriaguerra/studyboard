package studyboard.view.swing;

import java.awt.*; // Component, Color, Font, BorderLayout
import java.time.*; // LocalDate
import java.time.format.*; // DateTimeFormatter
import java.util.Map;  // subject names
import javax.swing.*; // JPanel, JLabel, JList, BorderFactory, ListCellRenderer, UIManager
import studyboard.model.*;

/**
 * Draws one task as a "card" inside a JList.
 * A JList asks a "renderer" how to build a component for each row. The same component is reused for every row 
 * (it is only a stamp that is repainted), which is why the fields below are created once.
 * The colour of the stripe is not the only signal: the symbol (!, !!) and the word late are also written.
 */
public class TaskCardRenderer implements ListCellRenderer<Task> {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM"); //the date format used in the task cards

    private Map<Integer, String> subjectNames = Map.of(); // subjectId -> name (sent by the Controller)
    private LocalDate today = LocalDate.now(); //to know if a task is late

    private final JPanel outer = new JPanel(new BorderLayout()); //transparent frame that creates the gap between cards, so the cards don't touch each other
    private final JPanel card = new JPanel(new BorderLayout(0, 2)); // the card itself
    private final JLabel title = new JLabel(); //first line, symbol + title
    private final JLabel details = new JLabel(); //second line: subject, date, hours

    public TaskCardRenderer() {
        title.setFont(title.getFont().deriveFont(Font.BOLD)); // bold title
        details.setFont(details.getFont().deriveFont(Font.PLAIN, 11f)); //a bit smaller (11 points)
        card.add(title, BorderLayout.NORTH);
        card.add(details, BorderLayout.CENTER);
        outer.add(card, BorderLayout.CENTER);
        outer.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4)); // 3 px above/below and 4 px at the sides
    }

    //The View calls this before every redraw, so the renderer knows the current names and date.
    public void update(Map<Integer, String> subjectNames, LocalDate today) {
        this.subjectNames = subjectNames;
        this.today = today;
    }

    //Colour of the left stripe. It reinforces the priority, but the text symbol says the same.
    private static Color priorityColor(Priority priority) {
        return switch (priority) {
            case HIGH -> new Color(0xE5484D); //red
            case MEDIUM -> new Color(0xF5A524); //amber
            case LOW -> new Color(0x8A94A6); //grey/blue
        };
    }

    @Override // called by the JList once for every visible row
    public Component getListCellRendererComponent(JList<? extends Task> list, Task task, int index, boolean isSelected, boolean cellHasFocus) {

        boolean late = task.isOverdue(today);
        String symbol = task.getPriority().getSymbol().isBlank() ? "" : task.getPriority().getSymbol() + " "; //add a space after the symbol if it is not empty
        title.setText(symbol + (late ? "LATE  " : "") + task.getTitle()); //if its late, write it 
        details.setText(subjectNames.getOrDefault(task.getSubjectId(), "?") // the subject name (or ? if the subject was deleted)
                + "  ·  due " + task.getDeadline().format(SHORT_DATE) // the deadline in dd.MM format
                + "  ·  " + task.getEstimatedHours() + " h"); // the estimated hours

        // Colours: the list gives us the "selected" colours, so the card follows the theme (light / dark)
        // "TextField.background" is white in the light theme and dark grey in the dark theme: the card stands out from the column
        // If the card is selected, use the selection colours; otherwise, use the default text field background

        Color cardBackground = isSelected ? list.getSelectionBackground() : UIManager.getColor("TextField.background"); // the card background colour
        Color textColor = isSelected ? list.getSelectionForeground() : list.getForeground(); // the text colour (black in light theme, white in dark theme)
        outer.setBackground(list.getBackground()); //the gap uses the colour of the column
        card.setBackground(cardBackground);
        title.setForeground(late && !isSelected ? new Color(0xD1242F) : textColor); // if the task is late, the title is red (unless it is selected, then it is white)
        details.setForeground(textColor);

        // Left stripe (4 px) with the priority colour + inner padding
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, priorityColor(task.getPriority())), // 4 px left stripe with the priority colour
                BorderFactory.createEmptyBorder(6, 8, 6, 8))); // 6 px above/below and 8 px at the sides
        return outer; // the JList paints this component in the row
    }
}
