package studyboard.model;

/**
 * What the Controller tells the View about the current board:
 * which filter is active, how the tasks are sorted and how many tasks exist in total.
 */
public record BoardOptions(TaskFilter filter, SortOrder sort, int totalTasks) { }

//Its empty because it is a record, which is a special kind of class that only has data and no methods.
