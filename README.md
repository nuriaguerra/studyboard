# StudyBoard

A text mode study planner for the terminal, written in Java with [Lanterna](https://github.com/mabe02/lanterna). This is my Project 1 for Human-Computer Interaction.

I have a few courses running at the same time, and the hard part is never writing a task down. The hard part is deciding what to do first. StudyBoard tries to answer that: it keeps your tasks on a Kanban board and has a "Today" screen that suggests the next task and tells you why.

## What it does

- **Tasks and subjects.** Create, edit and delete them. Every task has a title, notes, a subject, a priority, a deadline and an estimate in hours.
- **Kanban board** with three columns: To do, In progress and Done. Tasks move between columns with one key.
- **Today screen.** It scores the pending tasks and shows the best one, with the reasons ("Overdue by 1 day", "High priority"...). You can start it or mark it as done from there.
- **Search, filters and sorting.** Quick search with `/`, or a full form with `F`. The active filter is always shown at the top of the board.
- **Monthly calendar** with the deadlines of each day.
- **Undo** for the last 20 changes.
- **Context help** on every screen (`H` or `F1`).
- **Automatic saving.** There is no save button. Every change is written to disk.

## Requirements

- Java 21 or newer
- Maven (only if you want to build it yourself)
- A terminal that supports colours and is at least about 80x24 characters

## Build and run

```bash
mvn clean package -DskipTests
java -jar target/studyboard.jar
```

If you prefer a separate window instead of the terminal (or your terminal gives problems with some keys), run:

```bash
java -jar target/studyboard.jar --swing
```

The first time you start it, it loads some example data so the board is not empty.

## Keys

**Board**

| Key | Action |
|---|---|
| Arrows / Tab | Move between tasks and columns |
| Enter | Show the details of a task |
| N / E / D | New / edit / delete a task (delete asks first) |
| M / B | Move the task to the next / previous column |
| U | Undo the last change |
| / | Quick search |
| F | Filter and sort |
| X | Clear the filter |
| T | Today |
| S | Subjects |
| C | Calendar |
| H or F1 | Help |
| Q | Quit |

Every other screen has its own help, and `Esc` always goes back.

## How "Today" decides

Each pending task gets points and the highest score wins:

| Rule | Points |
|---|---|
| Deadline: overdue / today / within 2 days / 7 days / 14 days / later | 60 / 55 / 45 / 30 / 15 / 5 |
| Priority: High / Medium / Low | 30 / 15 / 0 |
| Big task (4 h or more) due within 7 days | +10 |
| Quick task (1 h or less) | +5 |
| Already in progress | +5 |

I wanted something simple that I could explain and change easily, and the screen always shows which rules gave the points, so the app suggests but never decides for you.

## Project structure

The project follows the MVC pattern. The idea is that for Project 2 only the View has to be replaced.

```
src/main/java/studyboard/
├── Main.java                  Creates the three parts and connects them
├── model/                     Data and rules, no user interface code
│   ├── Task, Subject, TaskData
│   ├── TaskRepository         Keeps everything in memory
│   ├── FileStorage            Saves and loads the data file
│   ├── TaskScorer             The "what next?" logic
│   └── TaskFilter, SortOrder, BoardOptions, Status, Priority ...
├── view/
│   ├── View.java              What the controller can ask a screen to do
│   ├── ViewListener.java      What the screen reports back (the user's actions)
│   └── lanterna/
│       └── LanternaView.java  The text mode implementation
└── controller/
    └── Controller.java        Changes the model and tells the view what to draw
```

A few rules I tried to keep:

- The **model** knows nothing about the other two parts.
- The **controller** only knows the `View` interface, never Lanterna.
- The **view** never changes data. It only reports what the user wants through `ViewListener`, and the controller decides what to do.

This means a graphical interface only needs a new class that implements `View`.

## Design decisions

These are the usability choices I made and why.

- **The user always knows what is going on.** The top bar shows the date, the sorting and "Showing X of Y tasks". An active filter is written in bold. Every action ends with a message at the bottom.
- **Mistakes are hard to make.** Deleting asks for confirmation and the safe answer ("No") is the default one. Deleting a subject says how many tasks will disappear. The form checks dates strictly, so `31.02.2026` is rejected, and the form stays open so you can fix it.
- **Nothing is lost.** Changes are saved automatically and Undo covers the last 20. If the data file is damaged, the app keeps a copy of it and starts with example data instead of crashing.
- **Consistent keys.** `Esc` goes back and `H` shows help on every screen, and `N / E / D` mean the same thing for tasks and subjects.
- **Shortcuts for experienced users, visible options for beginners.** `/` finds a task in two key presses, while the filter form shows every option on screen.
- **Colour is never the only signal.** Priority has symbols (`!` and `!!`), late tasks say `LATE`, today is in brackets in the calendar and days with deadlines are marked with `*` or `+`.
- **Clear error messages.** For example: "The deadline must be a real date written as DD.MM.YYYY".

One thing I changed along the way: the subjects screen used to have buttons, but moving down to them with the arrows selected the last subject by accident, so the wrong one could be deleted. I removed the buttons and now the list always keeps the focus.

## Where the data is stored

Everything is saved in a plain text file at `~/.studyboard/data.txt`. If you delete the `~/.studyboard` folder, the app starts again with the example data.

## Limitations and ideas for later

- Deleting a subject deletes its tasks too (you can undo it). Archiving subjects would be a gentler option.
- There is no high contrast theme yet.
- I would like to add a focus timer and a weekly workload view.
- Project 2 will reuse the model and the controller with a graphical view.

## Author

[Your name], [your study group / student ID]
Human-Computer Interaction, [semester / year]
