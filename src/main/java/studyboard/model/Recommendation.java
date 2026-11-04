package studyboard.model;

import java.util.*; // List

/**
 * Resultado de puntuar UNA tarea: la tarea, sus puntos y los motivos
 * (textos que lee el usuario en la pantalla Today). Es un record: solo datos.
 */
public record Recommendation(Task task, int score, List<String> reasons) { }
