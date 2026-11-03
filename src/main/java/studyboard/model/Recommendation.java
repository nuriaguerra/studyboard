package studyboard.model;

import java.util.List;

/**
 * The result of scoring one task: the task, its score and the reasons (texts the user
 * will read) that explain the score. A record is a small immutable data class.
 */
public record Recommendation(Task task, int score, List<String> reasons) { }