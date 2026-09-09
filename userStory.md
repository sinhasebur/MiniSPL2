# ProgressPath Detailed User Story

## Story: Plan, Perform, and Review Academic Progress

### Persona

Nadia is a university student taking several courses in one semester. Each course has chapters of different sizes, assignments with different priorities, and deadlines spread throughout the term. She currently uses separate notes, timers, and task lists, so she cannot easily determine whether her study progress matches her academic schedule.

### User Story

**As a university student managing multiple courses, I want to create a dated study plan, divide each course into weighted chapters, track chapter completion and assignments, and record focused study sessions so that I can compare my actual progress with my planned progress, discover where I am falling behind, and decide what to study next.**

### Value to the User

- All academic planning and progress information is available in one place.
- Weighted chapters make the progress calculation reflect workload instead of treating every chapter as equal.
- Expected-versus-actual comparison gives the student an early warning before a deadline is missed.
- Assignment filters and risk reports help the student prioritize the next action.
- Focus-session history shows how study time relates to academic progress.

## Preconditions

- ProgressPath has been installed and can access its SQLite database.
- The database schema has been initialized.
- The application is designed for one local student profile; login is not required for the MVP.
- The student knows the semester dates, courses, chapter targets, and assignment deadlines they want to enter.

## Main Success Scenario

1. Nadia opens ProgressPath and selects **Create Study Plan**.
2. She enters the plan name, start date, and end date.
3. She adds each course included in the plan.
4. For every course, she adds its chapters, arranges them in study order, assigns positive weights totalling 100%, and provides target dates.
5. ProgressPath validates the complete plan. Nadia corrects any invalid dates, missing information, or weight totals before saving.
6. The application saves the plan, courses, and chapters in one database transaction and displays them on the dashboard.
7. Nadia adds assignments with a title, course, deadline, priority, and initial status.
8. Before studying, she opens a chapter and starts a focus session linked to it.
9. She may pause and resume the timer. When she finishes, she completes the session and optionally enters a note.
10. ProgressPath calculates the session duration from timestamps and stores it permanently.
11. Nadia updates the chapter's completion percentage.
12. The application validates the new percentage, saves a progress-history record, and recalculates the course and overall plan progress using the selected progress strategy.
13. The application calculates expected progress from chapter weights and target dates, compares it with actual progress, and classifies the plan as ahead, on track, slightly behind, or significantly behind.
14. Progress-change observers refresh the dashboard, charts, and risk indicators.
15. Nadia reviews upcoming assignments, at-risk courses, and recent focus time to decide what to study next.
16. She closes and later reopens the application. Her plan, progress history, assignments, and completed focus sessions remain available.

## Alternative and Error Scenarios

### Invalid Plan Dates

- If the end date is not later than the start date, the application explains the problem and does not save the plan.
- If a chapter target date falls outside the study-plan period, the application asks Nadia to correct it.

### Invalid Chapter Weights

- If a weight is zero or negative, the application rejects it.
- If the weights for a course do not total 100%, the application shows the current total and prevents final plan submission.

### Invalid Progress Update

- If completion is outside 0–100%, the application rejects the value.
- If Nadia tries to reduce recorded progress, the application asks for confirmation and still preserves the previous history records.

### Focus Session Interruption

- Only a running session can be paused or completed.
- Only a paused session can be resumed.
- If the application closes during an active session, the next launch asks Nadia to resume the session or close it using the last saved timestamp.
- A cancelled session is not included in completed-focus-time analytics.

### Assignment Conditions

- An incomplete assignment whose deadline has passed is displayed as overdue.
- Completing an assignment preserves it for reporting instead of deleting it.
- Invalid or missing deadlines produce a clear validation message.

### Database Failure

- If a multi-entity save fails, the transaction is rolled back so that a partial plan is not created.
- The application shows an understandable error without exposing SQL details and keeps Nadia's entered form data where practical.

## Acceptance Criteria

### AC1 — Create a valid study plan

**Given** Nadia has entered a unique plan name and valid start and end dates  
**When** she adds at least one valid course and submits the plan  
**Then** the plan and its courses are stored and remain available after an application restart.

### AC2 — Validate the plan hierarchy

**Given** Nadia is configuring chapters for a course  
**When** a chapter has an invalid target date or the chapter weights do not total 100%  
**Then** ProgressPath identifies the affected field and prevents final submission until it is corrected.

### AC3 — Calculate weighted actual progress

**Given** a course has chapters with valid weights and completion percentages  
**When** Nadia updates a chapter's completion  
**Then** actual course progress is recalculated as the sum of each chapter's completion multiplied by its weight.

### AC4 — Preserve progress history

**Given** a saved chapter currently has a recorded completion value  
**When** Nadia changes that value  
**Then** ProgressPath creates a timestamped progress record and does not overwrite previous history.

### AC5 — Compare expected and actual progress

**Given** the plan has chapter target dates and weights  
**When** the dashboard is opened or progress changes  
**Then** ProgressPath displays actual progress, expected progress, their variance, and the documented status classification.

### AC6 — Enforce focus-session states

**Given** Nadia has selected a course or chapter  
**When** she starts, pauses, resumes, and completes a focus session  
**Then** only actions valid for the current state are accepted and the completed duration is stored once.

### AC7 — Recover an interrupted session

**Given** an active focus session was persisted before the application closed  
**When** Nadia launches ProgressPath again  
**Then** she is offered a clear choice to resume or close that session without silently losing its data.

### AC8 — Manage and locate assignments

**Given** assignments exist for several courses  
**When** Nadia filters by text, course, status, priority, or date range  
**Then** only matching assignments are shown and incomplete past-due assignments are marked overdue.

### AC9 — View useful analytics

**Given** progress records and completed focus sessions exist  
**When** Nadia opens weekly analytics  
**Then** she can see progress change, study time, and course-level risk for the selected week.

### AC10 — Maintain data consistency

**Given** a plan containing multiple related records is being saved  
**When** any database operation in that save fails  
**Then** all changes from that operation are rolled back and no partial plan remains.

## Completion Definition

The story is complete when all acceptance criteria pass, the important business rules have automated tests, data persists across restarts, and Nadia can perform the main scenario from the JavaFX interface without manually editing the database.
