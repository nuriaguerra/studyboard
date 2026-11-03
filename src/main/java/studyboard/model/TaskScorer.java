package studyboard.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides "what should I do next?". Pure logic: no UI, no files.
 * Simple on purpose: a table of points that is easy to explain and to change.
 */
public final class TaskScorer {
    private TaskScorer() { } // utility class: nobody creates objects of it

    /** Pending tasks (not Done), best suggestion first. */
    public static List<Recommendation> rank(List<Task> tasks, LocalDate today) {
        List<Recommendation> result = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getStatus() != Status.DONE) { // finished tasks are never suggested
                result.add(score(task, today));
            }
        }
        // Highest score first. Ties: earlier deadline first, then lower id (stable order).
        result.sort(Comparator.comparingInt(Recommendation::score).reversed()
                .thenComparing(r -> r.task().getDeadline())
                .thenComparingInt(r -> r.task().getId()));
        return result;
    }

    /** Gives points to ONE task and records a reason for every rule that applied. */
    public static Recommendation score(Task task, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        int points = 0;
        long days = task.daysUntilDeadline(today); // negative = overdue

        // Rule 1: how close is the deadline? (always applies, so there is always a reason)
        if (days < 0) {
            points += 60;
            reasons.add("Overdue by " + daysText(-days));
        } else if (days == 0) {
            points += 55;
            reasons.add("Due today");
        } else {
            if (days <= 2) {
                points += 45;
            } else if (days <= 7) {
                points += 30;
            } else if (days <= 14) {
                points += 15;
            } else {
                points += 5;
            }
            reasons.add(days == 1 ? "Due tomorrow" : "Due in " + daysText(days));
        }

        // Rule 2: priority chosen by the user.
        switch (task.getPriority()) {
            case HIGH -> { points += 30; reasons.add("High priority"); }
            case MEDIUM -> { points += 15; reasons.add("Medium priority"); }
            case LOW -> { } // no points and nothing to explain
        }

        // Rule 3: effort. Big tasks with a close deadline must be started early.
        double hours = task.getEstimatedHours();
        if (hours >= 4 && days <= 7) {
            points += 10;
            reasons.add("Big task (" + hours + " h) with a close deadline: start early");
        } else if (hours <= 1) {
            points += 5;
            reasons.add("Quick win (about 1 hour or less)");
        }

        // Rule 4: finishing what you started is better than starting something new.
        if (task.getStatus() == Status.IN_PROGRESS) {
            points += 5;
            reasons.add("Already in progress");
        }

        return new Recommendation(task, points, reasons);
    }

    private static String daysText(long n) {
        return n + (n == 1 ? " day" : " days");
    }
}