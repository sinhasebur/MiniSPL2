# ProgressPath — Educational Progress Tracker

## Project Proposal

**ProgressPath** is a desktop-based educational planning and progress management system designed to help students organize academic goals, monitor syllabus completion, manage important tasks, and evaluate whether their study progress is aligned with planned targets.

The application will be developed using **JavaFX**, **Maven**, and **SQLite**, with emphasis on maintainable architecture, meaningful business workflows, persistent data management, analytical features, and appropriate use of software design patterns.

## Problem Statement

Students often manage multiple courses, chapters, assignments, deadlines, and study goals simultaneously. Although traditional planners can record tasks, they usually provide limited insight into overall academic progress or whether a student is progressing according to schedule.

ProgressPath aims to provide a unified system where students can plan their academic work, track completed and remaining content, monitor progress over time, and identify areas that require attention.

## Core Scope

The system is expected to provide:

* Academic study-plan management
* Course and chapter organization
* Completed and remaining syllabus tracking
* Automatic progress calculation
* Expected vs. actual progress comparison
* Progress history and visual analytics
* Daily, weekly, and monthly progress views
* Assignment and important-task management
* Study and focus-session tracking
* Reminders and quick notes
* Search, filtering, and reporting capabilities
* Centralized dashboard for academic status and productivity

The application will contain multiple interconnected workflows rather than functioning only as a collection of CRUD operations.

## Progress and Analytics

A major component of the system will be continuous progress analysis.

The application will maintain historical information about student progress and compare current completion against planned progress. This will allow the system to determine whether a student is:

* Ahead of schedule
* On track
* Slightly behind
* Significantly behind

Progress information will be presented through appropriate charts, reports, and summary views so that students can observe changes over different periods and make better planning decisions.

## Focus and Productivity Features

ProgressPath will also include productivity-oriented features to support execution of academic plans.

These may include structured focus sessions, study-time tracking, pending-assignment monitoring, reminders, priorities, and important notes.

These features will be integrated with the broader planning and analytics system so that academic progress and study activity can be viewed together.

## System Design

The project will follow a structured architecture with appropriate separation between:

* JavaFX presentation components
* Application and business logic
* Design-pattern-related components
* Data-access and persistence components
* SQLite database

The database will contain multiple related entities representing the major parts of the academic planning system and will use appropriate keys, relationships, constraints, and persistent storage.

A database initialization and seeding mechanism will also be included.

## Design Patterns

The project is expected to provide several natural opportunities for applying design patterns.

Possible areas include:

* interchangeable progress-calculation mechanisms,
* lifecycle and status management,
* communication between dependent components,
* creation of different analytical or reporting operations.

Patterns will be selected according to actual design requirements rather than being introduced only for demonstration purposes.

Each significant pattern used in the final implementation will be documented with:

* the problem it solves,
* the reason for selecting it,
* alternative approaches,
* implementation details,
* maintainability and extensibility benefits.

## Testing and Reliability

The project will include testing for important business logic and major application workflows.

Validation and error handling will also be incorporated for areas such as:

* user input,
* dates and deadlines,
* status transitions,
* progress calculations,
* database operations,
* persistence and relationships.

## Project Deliverables

The final project will include:

* Working JavaFX application
* Maven project configuration
* SQLite database and database resources
* Complete source code
* Database initialization/seeding
* UML class diagrams
* Database ER diagram
* Appropriate test cases
* Project documentation
* GitHub repository with collaborative development history

Development will follow appropriate Git branching, merging, and contribution practices throughout the project lifecycle.

## Future Scope

The architecture will allow the application to grow beyond the initial academic version.

Possible future extensions include:

* AI-assisted study planning
* Intelligent progress recommendations
* Automatic schedule adjustment
* Advanced notifications
* Additional analytics
* External integrations
* Cloud synchronization
* Native desktop packaging

## Current Status

**Project Phase: Minimal MVC Application**

The current implementation provides one clean JavaFX MVC shell with seven tabs for study plans, course/chapter setup, progress history, assignments, focus sessions, and analytics. SQLite persistence, Strategy/State/Observer/Repository examples, validation, and a local data store are included. Charts, notifications, and advanced search remain suitable follow-up features.

## Run the Prototype

From the `ProgressPath` directory:

```bash
mvn test
mvn javafx:run
```

The application creates `data/progresspath.db` on its first run. The generated database is ignored by Git.

## Seed Demo Data

The seeder creates the schema and inserts a repeatable demo dataset of 11 study plans (22 courses, 61 weighted chapters, 30 progress history rows, 43 assignments, 30 focus sessions) covering topics like CS coursework, research methods, exam prep, capstone, archived history, web-dev bootcamp, Japanese, fitness, reading, music, and interview prep. Each chapter's weight sums to 100 within its course, and progress values are spread across the full 0–100% range so dashboards have something to render. It is idempotent: running it again on a populated database does nothing.

From the `ProgressPath` directory:

```bash
mvn -q -DskipTests package
mvn -q exec:java -Dexec.mainClass=com.progresspath.app.SeedRunner
```

If the `exec-maven-plugin` is not configured yet, use the explicit classpath form instead:

```bash
mvn -q dependency:copy-dependencies -DskipTests
java -cp "target/classes;target/dependency/*" com.progresspath.app.SeedRunner
```

The seeded values can then be inspected with any SQLite client, for example the `sqlite3` CLI shipped with Git for Windows:

```bash
sqlite3 -header -column data/progresspath.db "SELECT id, name, start_date, end_date FROM study_plans;"
sqlite3 -header -column data/progresspath.db "SELECT c.code, ch.name, ch.weight, ch.progress, ch.target_date FROM chapters ch JOIN courses c ON c.id = ch.course_id ORDER BY c.code, ch.target_date;"
sqlite3 -header -column data/progresspath.db "SELECT title, due_date, priority, status FROM assignments ORDER BY due_date;"
sqlite3 -header -column data/progresspath.db "SELECT id, previous_progress, new_progress, recorded_at FROM progress_records ORDER BY recorded_at;"
```

To re-seed from scratch, delete the SQLite file first and run the seeder again:

```bash
rm data/progresspath.db
mvn -q exec:java -Dexec.mainClass=com.progresspath.app.SeedRunner
```
