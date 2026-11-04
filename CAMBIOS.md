# StudyBoard – estado del proyecto (documento de traspaso para otro Claude)

> Pega este archivo al empezar una conversación nueva. Describe quién es el usuario, qué se pide, qué hay hecho y qué falta.

## 1. Contexto y usuaria
- Asignatura: **Human-Computer Interaction (Erasmus, Chequia)**. 3 proyectos evaluados por **buenas prácticas de UX** (no por tests):
  P1 = app en modo texto con MVC (este) · P2 = la misma app con GUI (solo se cambia la View) · P3 = diseño UX (wireframes, navegación, style guide, prototipo ~10 pantallas).
- Se presenta en persona al profesor y se sube a CEZ (código) + GitHub opcional. El profesor recomienda **Java + Lanterna**.
- Usuaria: nivel Java básico, **nunca usó Lanterna ni tests**. Sistema: **Fedora + VS Code**, Maven, JDK 21 (`dnf install java-21-openjdk-devel maven`).
- Preferencias: respuestas en **español**; código, mensajes de pantalla y README en **inglés**; comentarios del código en **español**, muy abundantes (casi cada línea); código simple, sin repeticiones, imports con `*`; **gastar pocos tokens**; dar pasos exactos. Quiere sacar un 10 y poder defenderlo.
- Se propuso al profesor (y se aprobó): tareas y asignaturas, tablero Kanban (To do / In progress / Done), pantalla de ayuda, confirmación antes de borrar, búsqueda, filtros, calendario mensual con fechas límite, undo y pantalla "Today" (sugiere la siguiente tarea y explica por qué).

## 2. Cómo se compila y ejecuta
```
mvn package
java -jar target/studyboard.jar          # terminal real (Fedora: funciona bien)
java -jar target/studyboard.jar --swing  # ventana emulada (por si falla el terminal)
```
Datos guardados en `~/.studyboard/data.txt` (borrar `~/.studyboard` = volver a los datos de ejemplo).
`pom.xml`: Lanterna 3.1.3, JUnit 5 (scope test), surefire, shade (mainClass `studyboard.Main`). No debe haber un `+` suelto antes del comentario de Surefire.
**`src/test` está FUERA del proyecto** (en `~/test-backup`) porque un test que falle impide generar el `.jar` y el enunciado no pide tests.

## 3. Arquitectura (MVC) – paquete `studyboard`
| Archivo | Papel |
|---|---|
| `Main` | Crea Model, View y Controller y los conecta. `--swing` = emulador. |
| `model/Status, Priority, SortOrder` | Enums (columnas, prioridades con símbolo `!`/`!!`, órdenes con Comparator). |
| `model/Subject, Task` | Datos + validación (lanzan `IllegalArgumentException`). |
| `model/TaskData` | record del formulario; su constructor compacto valida todo. |
| `model/TaskFilter` | record: texto, asignatura, prioridad, ocultar Done. `NONE`, `matches`, `describe`, `withText`. |
| `model/TaskScorer, Recommendation` | Puntuación de "¿qué hago ahora?" (deadline, prioridad, esfuerzo, en progreso) con motivos legibles. |
| `model/TaskRepository` | Listas en memoria, ids únicos, CRUD de tareas y asignaturas, `copy()`/`restoreFrom()` (para Undo), `restoreX` (cargar fichero). |
| `model/FileStorage` | Guardado/carga en texto con tabuladores, escape de `\t \n \\`, guardado atómico (.tmp), backup si el fichero está dañado. |
| `model/SampleData, BoardOptions` | Datos de ejemplo (8 tareas, 4 asignaturas) / record con filtro+orden+total para la View. |
| `view/View, ViewListener` | Interfaces. La View solo dibuja y avisa por `listener.onXxx`; **nunca cambia datos**. |
| `view/lanterna/LanternaView` | Toda la UI de texto (tablero, formulario, Today, Subjects, filtro, ayuda). |
| `controller/Controller` | Implementa `ViewListener`; cambia el Model, guarda tras cada cambio (`saveAndRefresh`), mantiene la pila de Undo. No conoce Lanterna. |

## 4. Cómo se usa (teclas)
Tablero: flechas/Tab moverse · Enter detalles · N nueva · E editar · D borrar (confirma, "No" primero) · M/B mover de columna · U deshacer · / buscar · F filtro y orden · X quitar filtro · T Today · S asignaturas · **H o F1 ayuda** · Q salir.
Subjects (sin botones, a propósito): flechas · Enter menú (Rename/Delete) · N · R · D · U · H · Esc.
Formularios: Tab, Esc cancela, F1 ayuda. Today: Tab/Enter/Esc/H.

## 5. Historial de cambios (respecto al código original de la usuaria)
1. Imports unificados con `*`; eliminados `java.lang.classfile.Label` y `javax.swing.border.Border` (rompían la compilación).
2. `run()` ya no abre Today al arrancar; usa `gui.addWindowAndWait(window)`.
3. Refactor en LanternaView: `closeOnEsc`, `confirm`, `selectedTaskOrWarn` (antes código copiado 2-4 veces).
4. Todos los archivos de `src/main` reescritos con comentarios línea a línea en español.
5. **Bug de Subjects**: con botones Delete/Rename, al bajar con flechas hasta ellos la selección pasaba por la última asignatura y siempre borraba esa. Solución: quitar los botones; Enter abre menú (`ActionListDialogBuilder`) y D actúa sobre la resaltada. `deleteSubject(Subject)` compartido.
6. **Ayuda contextual** (`helpOnKey` + constantes `BOARD_HELP`, `TODAY_HELP`, `SUBJECTS_HELP`, `TASK_FORM_HELP`, `FILTER_HELP`). F1 en todas; H/? solo donde no hay cajas de texto. Se usan letras además de F1 porque algunos terminales capturan F1.
7. **Undo** multinivel (máx. 20): el Controller guarda `repository.copy()` antes de cada cambio y lo apila solo si el cambio tuvo éxito (`remember`). `ViewListener.onUndo()`; tecla U en tablero y en Subjects (en Subjects la View llama `onUndo()` y luego `onShowSubjects()`). El filtro no se restaura con Undo, pero se limpia si apuntaba a una asignatura que ya no existe.

## 6. Estado de verificación
- Hasta el punto 5 la usuaria compiló y probó en Fedora ("funciona perfecto").
- Los puntos 6 y 7 (ayuda + Undo) se han escrito pero **aún no compilados/probados** por nadie: primer paso = `mvn package` y pegar errores exactos.

## 7. Pendiente (orden previsto)
1. Probar ayuda (F1/H) y Undo; arreglar lo que falle.
2. **Calendario mensual** con fechas límite (nueva pantalla; añadir `onShowCalendar` a `ViewListener`, método en `View`, tecla C).
3. Pulido UX: comportamiento al redimensionar el terminal, alto contraste (símbolos+texto ya evitan depender del color; valorar tema alternativo).
4. **README en inglés** para la entrega (instalación, ejecución, teclas, diseño MVC, decisiones de UX con heurísticas de Nielsen).
5. Guion de defensa ante el profesor (demo + respuestas sobre MVC y por qué la View se puede cambiar en el P2).
6. Opcional: devolver y comentar los tests (el `FakeView` debe tener UN `showBoard` de 4 parámetros, campo `BoardOptions lastOptions` y ahora también `onUndo` no aplica a la View, solo a ViewListener).
