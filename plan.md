# ProgressPath Initial Plan

## Goal

Build a JavaFX desktop application that lets a student create academic plans, track chapter and assignment progress, record focus sessions, and compare actual progress with scheduled targets.

## MVP Scope

- Manage study plans, courses, chapters, and assignments.
- Assign chapter weights and target dates.
- Record chapter progress and retain progress history.
- Run and save focus sessions linked to a course or chapter.
- Show expected versus actual progress on a dashboard.
- Provide assignment search and weekly/at-risk reports.
- Store all data in SQLite and include an idempotent database seeder.

Reminders, sticky notes, AI assistance, cloud sync, and OS notifications are stretch features.

## Features

### Planning

- Create, edit, archive, and delete study plans.
- Add courses and ordered chapters to a plan.
- Give chapters weights, target dates, and completion percentages.
- Validate plan dates and chapter-weight totals before saving.

### Progress Tracking

- Calculate course and overall plan progress automatically.
- Compare actual progress with expected progress.
- Classify progress as ahead, on track, slightly behind, or significantly behind.
- Preserve progress records so changes can be viewed over time.

### Assignments

- Create, edit, complete, and delete assignments.
- Set course, due date, priority, and status.
- Search and filter assignments by title, course, status, priority, and date.
- Highlight overdue and upcoming work.

### Focus and Productivity

- Start, pause, resume, cancel, and complete a focus session.
- Associate a session with a course or chapter.
- Persist completed session duration and notes.
- Summarize study time by day, week, course, and chapter.

### Dashboard and Analytics

- Display overall and per-course progress.
- Show expected-versus-actual variance and at-risk courses.
- Show upcoming assignments and recent focus activity.
- Produce a weekly progress report and an at-risk course report.
- Display progress-history and study-time charts.

### Reliability

- Persist data in SQLite across application restarts.
- Seed the database with repeatable demonstration data.
- Validate input and show understandable error messages.
- Recover or close an interrupted focus session when the app restarts.

## Core Data Model

- `StudyPlan` 1--* `Course`
- `Course` 1--* `Chapter`
- `Course` 1--* `Assignment`
- `Chapter` 1--* `ProgressRecord`
- `Course`/`Chapter` 1--* `FocusSession`

Define primary and foreign keys, date and percentage checks, status constraints, ordering, and delete behavior in an ER diagram before implementation.

## Business Rules

- Plan end date must be after its start date.
- Chapter weight must be positive; course chapter weights must total 100%.
- Actual progress is the weighted sum of chapter completion.
- Expected progress is calculated from chapter target dates and weights.
- Progress classifications and thresholds (`ahead`, `on track`, `slightly behind`, `significantly behind`) must be documented and tested.
- Assignment and focus-session status transitions must be validated.

## Main Workflows

1. **Plan setup:** create plan -> add courses -> add weighted chapters and target dates -> validate -> save in one transaction.
2. **Progress update:** record chapter completion -> save history -> recalculate progress -> compare with target -> refresh dashboard and alerts.
3. **Focus session:** start -> pause/resume -> complete -> calculate duration -> persist -> include in analytics.

## Design Patterns

- **Strategy:** `ProgressCalculationStrategy` provides interchangeable equal-chapter and weighted-chapter calculations. A new calculation policy can be added without modifying the progress service.
- **State:** `FocusSessionState` represents ready, running, paused, completed, and cancelled states. Each state controls which timer actions and transitions are valid.
- **Observer:** progress-change events notify the dashboard, analytics, and risk-alert components without coupling them directly to the chapter editor.
- **Repository:** repository interfaces isolate SQLite queries from services and JavaFX controllers, allowing persistence code to be replaced or tested independently.
- **Factory Method (only if reports require different construction):** a report creator may build weekly-progress and at-risk-course reports. If the only difference is a date range, use one parameterized report service instead of forcing this pattern.

Only retain a pattern if its problem, alternative, and extension benefit can be demonstrated.

## User Story

The detailed end-to-end user story and its acceptance criteria are documented in `userStory.md`.

## Screens

1. Dashboard
2. Study plans
3. Plan/course details
4. Chapter progress
5. Assignments
6. Focus timer
7. Analytics and reports

## Delivery Phases

1. Finalize requirements, formulas, ER diagram, UML, and workflow diagrams.
2. Implement SQLite schema, migrations/seeder, models, and repositories.
3. Implement and unit-test business services and design patterns.
4. Build JavaFX screens and navigation; keep controllers free of SQL.
5. Add reports, validation, error handling, and integration tests.
6. Polish documentation, seed demo data, and rehearse the final demonstration.

## Completion Checklist

- CRUD works for plans, courses, chapters, and assignments.
- At least two multi-step workflows and two analytical/search operations work.
- Data survives restart and foreign keys are enabled.
- Important formulas, transitions, validation, and repositories have tests.
- `mvn test` passes and the app runs with `mvn javafx:run`.
- Both members contribute through feature branches and reviewed merges/PRs.
- README, UML, ER diagram, schema/seeder, and demo instructions are complete.
