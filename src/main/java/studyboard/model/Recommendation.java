package studyboard.model;

import java.util.*; // List

/**
 * The result of scoring ONE task: the task itself, its score and the reasons
 * (texts that the user reads on the Today screen to understand the score).
 * It is a record: a small class that only holds data, with getters like r.task(), r.score(), r.reasons().
 */
public record Recommendation(Task task, int score, List<String> reasons) { }
