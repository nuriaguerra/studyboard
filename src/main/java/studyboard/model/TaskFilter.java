package studyboard.model;

import java.util.*; // List, ArrayList, Locale, Map

/**
 * What the user wants to see on the board. null / empty / false means "no restriction".
 * It is a record, so it is immutable: a filter is never modified, a new one is created instead.
 */
public record TaskFilter(String text, Integer subjectId, Priority priority, boolean hideDone) {
    //Integer (with capital I) can be null, a normal int cannot. Here null = "all subjects".

    /** The filter that lets everything through. static final = one single shared copy for the whole program. */
    public static final TaskFilter NONE = new TaskFilter("", null, null, false);

    /** Compact constructor: it runs on creation and cleans the search text. */
    public TaskFilter {
        text = (text == null) ? "" : text.trim(); //null becomes "" and the extra spaces are removed
    }

    /** True if at least one restriction is set. */
    public boolean isActive() {
        return !text.isEmpty() || subjectId != null || priority != null || hideDone;
    }

    /**
     * A task is shown only if it passes all the conditions that are set.
     * Each "if ... return false" is a test the task fails; if it survives all of them, it is shown.
     */
    public boolean matches(Task task, Map<Integer, String> subjectNames) {
        if (hideDone && task.getStatus() == Status.DONE) return false; // hide finished tasks
        if (subjectId != null && !subjectId.equals(task.getSubjectId())) return false; // another subject
        if (priority != null && task.getPriority() != priority) return false; // another priority
        if (!text.isEmpty()) { // the user typed something in the search box
            String wanted = text.toLowerCase(Locale.ROOT); // ROOT = same result on every computer and language
            String subject = subjectNames.getOrDefault(task.getSubjectId(), ""); //name of the task's subject
            // Look for the text in the title, the notes or the subject name (ignoring upper/lower case)
            boolean found = task.getTitle().toLowerCase(Locale.ROOT).contains(wanted)
                    || task.getNotes().toLowerCase(Locale.ROOT).contains(wanted)
                    || subject.toLowerCase(Locale.ROOT).contains(wanted);
            if (!found) return false; // the text was not found anywhere, so the task is filtered out
        }
        return true; // it passed every test
    }

    /** Same filter but with another search text (used by the quick search, the "/" key). */
    public TaskFilter withText(String newText) {
        return new TaskFilter(newText, subjectId, priority, hideDone); //a new record, the old one is untouched
    }

    /** Human readable summary for the screen, for example: search "lab", subject Physics. */
    //this is for line on top of the board, to describe the filter
    public String describe(Map<Integer, String> subjectNames) {
        List<String> parts = new ArrayList<>(); //one piece of text for each active condition
        if (!text.isEmpty()) parts.add("search \"" + text + "\""); // \" is a quote character inside a String
        if (subjectId != null) parts.add("subject " + subjectNames.getOrDefault(subjectId, "?"));
        if (priority != null) parts.add("priority " + priority.getLabel());
        if (hideDone) parts.add("done tasks hidden");
        return parts.isEmpty() ? "All tasks" : String.join(", ", parts); // join glues the parts with ", "
    }
}
