package studyboard.model;

import java.util.*; // List

/**
 * The result of scoring one task: the task itself, its score and the reasons for that score. The Controller uses this to show the Today screen.
 * Its empty because it is a record, which is a special kind of class that only has data and no methods.
 */
public record Recommendation(Task task, int score, List<String> reasons) { }
