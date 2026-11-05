package studyboard.model;

/**
 * What the Controller tells the View about the current board:
 * which filter is active, how the tasks are sorted and how many tasks exist in total.
 * (The total is needed to print "Showing 3 of 8 tasks" when a filter hides some of them.)
 * It is a record, so it is just a small immutable package of data.
 */
public record BoardOptions(TaskFilter filter, SortOrder sort, int totalTasks) { }
