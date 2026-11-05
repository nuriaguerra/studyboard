package studyboard.model;

import java.time.*; // LocalDate
import java.util.*; // List, ArrayList, Comparator

/**
 * Decides "what should I do next?". Pure logic: no screen code and no files.
 * It is a table of points that is easy to explain and easy to change: more points = do it earlier.
 * Every rule that gives points also writes a "reason", and those reasons are what the user
 * reads under "Why now?" on the Today screen.
 */
public final class TaskScorer {
    private TaskScorer() { } // utility class: nobody creates objects of it, we only call TaskScorer.rank(...)

    /** Pending tasks (not Done), best suggestion first. */
    public static List<Recommendation> rank(List<Task> tasks, LocalDate today) {
        List<Recommendation> result = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getStatus() != Status.DONE) { // finished tasks are never suggested
                result.add(score(task, today));
            }
        }
        // Highest score first (reversed). Ties: earlier deadline first, then lower id (stable order).
        // "r -> r.task().getDeadline()" is a lambda: "given a recommendation r, take the deadline of its task".
        result.sort(Comparator.comparingInt(Recommendation::score).reversed()
                .thenComparing(r -> r.task().getDeadline())
                .thenComparingInt(r -> r.task().getId()));
        return result;
    }

    /** Gives points to ONE task and records a reason for every rule that applied. */
    public static Recommendation score(Task task, LocalDate today) {
        List<String> reasons = new ArrayList<>(); // reasons the user will see
        int points = 0;
        long days = task.daysUntilDeadline(today); // negative = overdue

        // RULE 1: how close is the deadline? (always applies, so there is always at least one reason)
        if (days < 0) {
            points += 60;
            reasons.add("Overdue by " + daysText(-days)); // -days turns -3 into 3
        } else if (days == 0) {
            points += 55;
            reasons.add("Due today");
        } else {
            if (days <= 2) points += 45;
            else if (days <= 7) points += 30;
            else if (days <= 14) points += 15;
            else points += 5;
            reasons.add(days == 1 ? "Due tomorrow" : "Due in " + daysText(days));
        }

        // RULE 2: priority chosen by the user. The arrow switch runs only the matching branch.
        switch (task.getPriority()) {
            case HIGH -> { points += 30; reasons.add("High priority"); }
            case MEDIUM -> { points += 15; reasons.add("Medium priority"); }
            case LOW -> { } // no points and nothing to explain
        }

        // RULE 3: effort. A big task with a close deadline has to be started early.
        double hours = task.getEstimatedHours();
        if (hours >= 4 && days <= 7) {
            points += 10;
            reasons.add("Big task (" + hours + " h) with a close deadline: start early");
        } else if (hours <= 1) {
            points += 5;
            reasons.add("Quick win (about 1 hour or less)");
        }

        // RULE 4: finishing what you started is better than starting something new
        if (task.getStatus() == Status.IN_PROGRESS) {
            points += 5;
            reasons.add("Already in progress");
        }

        return new Recommendation(task, points, reasons); // task + total points + list of reasons
    }

    /** "1 day" / "3 days" (singular or plural). */
    private static String daysText(long n) {
        return n + (n == 1 ? " day" : " days");
    }
}
