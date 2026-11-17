# StudyBoard

A study planner for students with five or six courses at once. The hard part is not writing tasks down,
but knowing **what to do first**. StudyBoard keeps tasks and subjects on a Kanban board
(To do / In progress / Done) and its "Today" screen suggests the next task **and explains why**.

The same application has **two interchangeable user interfaces**:

| Project | Branch | Interface |
|---|---|---|
| Project 1 | `master` (tag `v1-project1`) | Text mode (Lanterna) |
| Project 2 | `project2` | Graphical (Swing + FlatLaf) |

Both use the **same Model and Controller**: only the View layer was replaced.

## Features
- Tasks (title, subject, priority, deadline, estimate, notes) and subjects
- Kanban board: move tasks with buttons, keys, or drag and drop (graphical version)
- Today: best next task, the reasons, and the next suggestions
- Search, filter (subject, priority, hide done) and sort (deadline, priority, title)
- Monthly calendar with deadlines
- Undo (up to 20 changes), confirmation before deleting, contextual help in every window
- Light and dark theme (graphical version)
- Data is saved automatically to `~/.studyboard/data.txt`

## Requirements and run
Java 21 and Maven.

```
mvn clean package -DskipTests
java -jar target/studyboard.jar              # graphical window (Project 2)
java -jar target/studyboard.jar --text       # text mode in a real terminal (Project 1)
java -jar target/studyboard.jar --emulator   # text mode in an emulated window
```
Delete `~/.studyboard` to start again with example data.

## Architecture (MVC)
```
Model       data and rules (no screen code)      studyboard.model
Controller  receives events, changes the Model,  studyboard.controller
            saves, tells the View what to draw
View        interface View + ViewListener        studyboard.view
            LanternaView (text)                  studyboard.view.lanterna
            SwingView + dialogs (graphical)      studyboard.view.swing
```
The Controller only knows the `View` interface. The View never changes data:
it calls `listener.onXxx(...)` and draws what the Controller sends with `showXxx(...)`.
`git diff v1-project1 project2 -- src/main/java/studyboard/model src/main/java/studyboard/controller`
is empty: Project 2 did not touch them.

## Usability (Nielsen's heuristics)
| Heuristic | Where |
|---|---|
| Visibility of system status | Message after every action; "FILTER ON" line; "Showing 3 of 8 tasks" |
| Match with the real world | Kanban columns, deadlines, DD.MM.YYYY dates |
| User control and freedom | Undo, Esc / Cancel in every window |
| Consistency and standards | Esc closes, F1 / H opens help, same actions in both interfaces |
| Error prevention | Confirmation with "No" as default; strict date validation; the form stays open on errors |
| Recognition rather than recall | Toolbar with tooltips, visible key hints, drop-down lists |
| Flexibility and efficiency | Shortcuts (Ctrl+N, Ctrl+Z, F2-F4), quick search, drag and drop |
| Aesthetic and minimalist design | Cards with only the needed data; one task per card |
| Help users recover from errors | Messages say what is wrong and give an example |
| Help and documentation | Contextual help in every window |

Colour is never the only signal: priority symbols (`!`, `!!`), the word `LATE`, and `*n` / `+n` marks are also written.

## Shortcuts (graphical version)
`Ctrl+N` new · `Ctrl+E` edit · `Delete` delete · `Ctrl+←/→` move · `Ctrl+Z` undo · `Ctrl+F` search · `F2` Today · `F3` Subjects · `F4` Calendar · `F1` help
