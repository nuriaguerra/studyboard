# StudyBoard

A study planner for students with several courses at once. Writing a task down is easy; knowing **what to do first** is the hard part. StudyBoard keeps tasks and subjects on a Kanban board (To do / In progress / Done), and its "Today" screen suggests the next task **and explains why**.

The same application has **two interchangeable interfaces**:

| Project | Branch | Interface |
|---|---|---|
| Project 1 | `master` (tag `v1-project1`) | Text mode, [Lanterna](https://github.com/mabe02/lanterna) |
| Project 2 | `project2` | Graphical, Swing + [FlatLaf](https://www.formdev.com/flatlaf/) |

Both use the **same Model and Controller**. Only the View layer was replaced.

## Features
- Tasks (title, subject, priority, deadline, estimate, notes) and subjects
- Kanban board: move tasks with buttons, keys or drag and drop (graphical version)
- Today: best next task, the reasons, and the following suggestions
- Search, filter (subject, priority, hide done) and sort (deadline, priority, title)
- Monthly calendar with deadlines
- Undo (up to 20 changes), confirmation before deleting, contextual help in every window
- Light and dark theme (graphical version)
- Automatic saving to `~/.studyboard/data.txt` (no save button)

## Build and run

```bash
mvn clean package -DskipTests
java -jar target/studyboard.jar              # graphical window (Project 2)
java -jar target/studyboard.jar --text       # text mode in a real terminal (Project 1)
java -jar target/studyboard.jar --emulator   # text mode in an emulated window
```
The first run loads example data. Delete `~/.studyboard` to start again with it.

## Controls

**Graphical version**

| Input | Action |
|---|---|
| Click / double-click a card | Select / show details |
| Drag a card to another column | Move it |
| `Ctrl+N` / `Ctrl+E` / `Delete` | New / edit / delete (asks first) |
| `Ctrl+←` / `Ctrl+→` | Move to the previous / next column |
| `Ctrl+Z` | Undo |
| `Ctrl+F` | Go to search (Enter applies it) |
| `F2` / `F3` / `F4` | Today / Subjects / Calendar |
| `F1` | Help of the current window |

**Text version**

| Key | Action |
|---|---|
| Arrows / Tab | Move between tasks and columns |
| Enter | Task details |
| `N` / `E` / `D` | New / edit / delete (asks first) |
| `M` / `B` | Move to the next / previous column |
| `U` | Undo |
| `/` | Quick search |
| `F` / `X` | Filter and sort / clear filter |
| `T` / `S` / `C` | Today / Subjects / Calendar |
| `H` or `F1` | Help |
| `Esc` / `Q` | Back / quit |

## How "Today" decides
Each pending task gets points and the highest score wins. The screen lists the rules that gave the points, so the app suggests but never decides for you.

| Rule | Points |
|---|---|
| Deadline: overdue / today / within 2 days / 7 days / 14 days / later | 60 / 55 / 45 / 30 / 15 / 5 |
| Priority: High / Medium / Low | 30 / 15 / 0 |
| Big task (4 h or more) due within 7 days | +10 |
| Quick task (1 h or less) | +5 |
| Already in progress | +5 |

## Architecture (MVC)
```
src/main/java/studyboard/
├── Main.java                  Chooses the View, connects the three parts
├── model/                     Data and rules, no interface code
│   ├── Task, Subject, TaskData, TaskRepository, FileStorage
│   ├── TaskScorer             The "what next?" logic
│   └── TaskFilter, SortOrder, BoardOptions, Status, Priority ...
├── view/
│   ├── View, ViewListener     What the Controller asks / what the screen reports
│   ├── lanterna/              Text mode (Project 1)
│   └── swing/                 Graphical mode (Project 2): SwingView, TaskCardRenderer, dialogs
└── controller/Controller      Changes the Model, saves, tells the View what to draw
```
- The **Model** knows nothing about the other parts and validates all data.
- The **Controller** only knows the `View` interface, never Lanterna or Swing.
- The **View** never changes data: it calls `listener.onXxx(...)` and draws what the Controller sends with `showXxx(...)`.

This command prints nothing, because Project 2 did not touch them:
```bash
git diff v1-project1 project2 -- src/main/java/studyboard/model src/main/java/studyboard/controller
```
Swing runs on its Event Dispatch Thread, so `Main` starts the Controller there. Forms, Today and Calendar are modal; the Subjects window is not, so it refreshes while open.

## Usability (Nielsen's heuristics)
| Heuristic | In StudyBoard |
|---|---|
| Visibility of system status | Message after every action; "FILTER ON" line; "Showing 3 of 8 tasks"; task counts in column titles |
| Match with the real world | Kanban columns, deadlines, DD.MM.YYYY dates |
| User control and freedom | Undo, Cancel / `Esc` in every window, "Clear filter" |
| Consistency and standards | Same actions in both interfaces; `Esc` closes; `F1` opens help |
| Error prevention | Confirmation with "No" as default; strict date validation (31.02.2026 is rejected); subject deletion says how many tasks are lost |
| Recognition rather than recall | Toolbar with tooltips, visible key hints, drop-down lists |
| Flexibility and efficiency | Shortcuts, quick search, drag and drop |
| Aesthetic and minimalist design | One card per task with only the needed data |
| Recover from errors | Messages say what is wrong and give an example; the form stays open; a damaged data file is backed up |
| Help and documentation | A help text per window, only for that window |

Colour is never the only signal: priority symbols (`!`, `!!`), the word `LATE`, and `*n` / `+n` calendar marks are also written.

Design note: in text mode the Subjects screen has no buttons, because moving down to them with the arrows selected the wrong subject.

## Limitations and ideas
- Deleting a subject deletes its tasks (undo is available); archiving would be gentler.
- No high contrast theme yet.
- Possible additions: a focus timer and a weekly workload view.

## Author
Nuria Guerra Casal, 97559@student.pb.edu.pl
Human-Computer Interaction, first semester 2026/2027
