package com.progresspath.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DatabaseSeeder {
    private final DatabaseManager databaseManager;

    public DatabaseSeeder(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void initialize() {
        try (Connection connection = databaseManager.connect();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS study_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        start_date TEXT NOT NULL,
                        end_date TEXT NOT NULL,
                        archived INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CHECK (date(end_date) > date(start_date))
                    )
                    """);
            ensureArchivedColumn(connection);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS courses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        study_plan_id INTEGER NOT NULL,
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (study_plan_id) REFERENCES study_plans(id) ON DELETE CASCADE,
                        UNIQUE (study_plan_id, code)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chapters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        course_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        weight REAL NOT NULL CHECK (weight > 0 AND weight <= 100),
                        progress REAL NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
                        target_date TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                        UNIQUE (course_id, name)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS progress_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        chapter_id INTEGER NOT NULL,
                        previous_progress REAL NOT NULL,
                        new_progress REAL NOT NULL,
                        recorded_at TEXT NOT NULL,
                        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
                        CHECK (previous_progress BETWEEN 0 AND 100),
                        CHECK (new_progress BETWEEN 0 AND 100)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS assignments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        course_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        due_date TEXT NOT NULL,
                        priority TEXT NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
                        status TEXT NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        course_id INTEGER NOT NULL,
                        chapter_id INTEGER,
                        started_at TEXT NOT NULL,
                        ended_at TEXT NOT NULL,
                        duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
                        status TEXT NOT NULL CHECK (status IN ('COMPLETED', 'CANCELLED')),
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_progress_chapter ON progress_records(chapter_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_assignment_due ON assignments(due_date)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_focus_started ON focus_sessions(started_at)");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not initialize the database.", exception);
        }
    }

    private void ensureArchivedColumn(Connection connection) throws SQLException {
        boolean found = false;
        try (Statement check = connection.createStatement();
             java.sql.ResultSet columns = check.executeQuery("PRAGMA table_info(study_plans)")) {
            while (columns.next()) {
                if ("archived".equals(columns.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            try (Statement migration = connection.createStatement()) {
                migration.executeUpdate("ALTER TABLE study_plans ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    /**
     * Inserts a realistic demo dataset so the dashboards, reports, and
     * progress calculations have something to render. The method is
     * idempotent: if any {@code study_plans} rows already exist it returns a
     * report with zero inserts and {@code dataAlreadyPresent=true} so the
     * caller can rerun it safely.
     *
     * <p>Demo shape (dates are relative to "today" so the demo never goes
     * stale):</p>
     * <ul>
     *     <li>11 study plans, each spanning roughly 90 days.</li>
     *     <li>2 courses per plan; each course has 3–4 weighted chapters (weights sum to 100).</li>
     *     <li>At least 1 progress record per plan.</li>
     *     <li>2–3 assignments per plan (mixed priorities and statuses).</li>
     *     <li>2 focus sessions per plan (mostly COMPLETED, occasionally CANCELLED).</li>
     * </ul>
     */
    public SeedingReport seedDemoData() {
        try (Connection connection = databaseManager.connect()) {
            connection.setAutoCommit(false);
            try {
                if (tableHasRows(connection, "study_plans")) {
                    connection.commit();
                    return new SeedingReport(0, 0, 0, 0, 0, 0, true);
                }

                LocalDate today = LocalDate.now();
                int totalPlans = 0;
                int totalCourses = 0;
                int totalChapters = 0;
                int totalProgressRecords = 0;
                int totalAssignments = 0;
                int totalFocusSessions = 0;

                for (PlanTemplate plan : PlanTemplate.all()) {
                    LocalDate planStart = today.plusDays(plan.startOffsetDays);
                    LocalDate planEnd = planStart.plusDays(plan.durationDays);

                    long planId = insertPlan(connection, plan.name, planStart, planEnd, plan.archived);
                    totalPlans++;

                    for (CourseTemplate course : plan.courses) {
                        long courseId = insertCourse(connection, planId, course.code, course.name);
                        totalCourses++;

                        long firstChapterId = -1L;
                        int chapterIndex = 0;
                        for (ChapterTemplate chapter : course.chapters) {
                            long chapterId = insertChapter(connection, courseId, chapter.name,
                                    chapter.weight, planStart.plusDays(chapter.targetOffsetDays));
                            totalChapters++;
                            if (chapterIndex == 0) {
                                firstChapterId = chapterId;
                            }
                            if (chapter.progress > 0.0) {
                                updateChapterProgress(connection, chapterId, chapter.progress);
                                insertProgressRecord(connection, chapterId,
                                        0.0, chapter.progress,
                                        planStart.plusDays(chapter.progressOffsetDays).atTime(10, 0));
                                totalProgressRecords++;
                            }
                            chapterIndex++;
                        }

                        for (AssignmentTemplate assignment : course.assignments) {
                            insertAssignment(connection, courseId,
                                    assignment.title,
                                    assignment.description,
                                    planStart.plusDays(assignment.dueOffsetDays),
                                    assignment.priority,
                                    assignment.status);
                            totalAssignments++;
                        }

                        for (FocusTemplate focus : course.focusSessions) {
                            insertFocusSession(connection, courseId, focus.linkedToFirstChapter ? firstChapterId : null,
                                    planStart.plusDays(focus.startOffsetDays).atTime(focus.startHour, 0),
                                    planStart.plusDays(focus.startOffsetDays).atTime(focus.endHour, focus.endMinute),
                                    focus.durationMinutes * 60L,
                                    focus.status,
                                    focus.notes);
                            totalFocusSessions++;
                        }
                    }
                }

                connection.commit();
                return new SeedingReport(totalPlans, totalCourses, totalChapters,
                        totalProgressRecords, totalAssignments, totalFocusSessions, false);
            } catch (SQLException exception) {
                connection.rollback();
                throw new DatabaseException("Could not seed demo data.", exception);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not open a connection to seed demo data.", exception);
        }
    }

    // ---------------------------------------------------------------------
    // Seed data templates (kept in one place so the SQL helpers stay small).
    // ---------------------------------------------------------------------

    private record PlanTemplate(
            String name,
            int startOffsetDays,
            int durationDays,
            boolean archived,
            CourseTemplate[] courses
    ) {
        static PlanTemplate[] all() {
            return new PlanTemplate[]{
                    new PlanTemplate("Fall 2026 — Sample Plan", -30, 90, false, new CourseTemplate[]{
                            new CourseTemplate("CSE301", "Data Structures", new ChapterTemplate[]{
                                    new ChapterTemplate("Arrays & Linked Lists", 25.0, 5, 100.0, -20),
                                    new ChapterTemplate("Stacks & Queues", 25.0, 20, 60.0, -3),
                                    new ChapterTemplate("Trees & Heaps", 25.0, 40, 0.0, 0),
                                    new ChapterTemplate("Graph Algorithms", 25.0, 60, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Linked List Lab",
                                            "Implement singly and doubly linked lists.", -5, "HIGH", "PENDING"),
                                    new AssignmentTemplate("Stack-based Evaluator",
                                            "Evaluate postfix expressions with a stack.", 2, "MEDIUM", "IN_PROGRESS"),
                                    new AssignmentTemplate("Binary Tree Traversals",
                                            "Recursive and iterative traversals.", 15, "HIGH", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-7, 9, 10, 30, 90, "COMPLETED",
                                            "Worked through array examples.", true),
                                    new FocusTemplate(-4, 14, 15, 15, 75, "COMPLETED",
                                            "Implemented stack-based evaluator.", true),
                                    new FocusTemplate(-1, 22, 22, 10, 10, "CANCELLED",
                                            "Got interrupted — will resume tomorrow.", false)
                            }),
                            new CourseTemplate("MAT210", "Discrete Mathematics", new ChapterTemplate[]{
                                    new ChapterTemplate("Logic & Proofs", 30.0, 10, 100.0, -15),
                                    new ChapterTemplate("Sets & Relations", 30.0, 25, 40.0, -2),
                                    new ChapterTemplate("Functions & Counting", 20.0, 45, 0.0, 0),
                                    new ChapterTemplate("Graph Theory Basics", 20.0, 60, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Proof Practice Set",
                                            "Work through 10 induction proofs.", 7, "MEDIUM", "COMPLETED"),
                                    new AssignmentTemplate("Counting Problems",
                                            "Permutations and combinations worksheet.", -10, "LOW", "CANCELLED")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-2, 20, 20, 45, 45, "COMPLETED",
                                            "Reviewed set operations.", true)
                            })
                    }),
                    new PlanTemplate("Spring 2027 — Research Sprint", 30, 75, false, new CourseTemplate[]{
                            new CourseTemplate("RES501", "Research Methods", new ChapterTemplate[]{
                                    new ChapterTemplate("Framing Questions", 25.0, 10, 0.0, 0),
                                    new ChapterTemplate("Literature Review", 35.0, 25, 0.0, 0),
                                    new ChapterTemplate("Data Collection", 40.0, 55, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Research Proposal",
                                            "Outline the dissertation topic.", 35, "HIGH", "PENDING"),
                                    new AssignmentTemplate("Annotated Bibliography",
                                            "Summarise 12 academic sources.", 50, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(2, 9, 10, 0, 60, "COMPLETED",
                                            "Drafted research questions.", true)
                            }),
                            new CourseTemplate("STA410", "Applied Statistics", new ChapterTemplate[]{
                                    new ChapterTemplate("Descriptive Statistics", 30.0, 15, 0.0, 0),
                                    new ChapterTemplate("Probability Basics", 30.0, 30, 0.0, 0),
                                    new ChapterTemplate("Hypothesis Testing", 40.0, 55, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Stats Homework 1",
                                            "Compute mean, median, variance for two datasets.", 40, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(3, 14, 15, 0, 60, "COMPLETED",
                                            "Worked through probability exercises.", true)
                            })
                    }),
                    new PlanTemplate("Summer 2026 — Exam Prep", -90, 60, false, new CourseTemplate[]{
                            new CourseTemplate("PHY201", "Classical Mechanics", new ChapterTemplate[]{
                                    new ChapterTemplate("Kinematics", 40.0, 10, 100.0, -30),
                                    new ChapterTemplate("Dynamics", 60.0, 30, 50.0, -5)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Kinematics Problem Set",
                                            "Solve 20 motion problems.", -20, "MEDIUM", "COMPLETED"),
                                    new AssignmentTemplate("Dynamics Quiz Prep",
                                            "Review Newton's laws.", -5, "HIGH", "COMPLETED"),
                                    new AssignmentTemplate("Mock Exam",
                                            "Timed practice test.", 5, "HIGH", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-15, 9, 10, 30, 90, "COMPLETED",
                                            "Reviewed dynamics problems.", true),
                                    new FocusTemplate(-3, 16, 17, 0, 60, "COMPLETED",
                                            "Mock exam run-through.", true)
                            }),
                            new CourseTemplate("CHE110", "General Chemistry", new ChapterTemplate[]{
                                    new ChapterTemplate("Atomic Structure", 35.0, 12, 100.0, -25),
                                    new ChapterTemplate("Bonding", 35.0, 28, 30.0, -7),
                                    new ChapterTemplate("Reactions", 30.0, 45, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Bonding Worksheet",
                                            "Lewis structures and VSEPR practice.", -10, "MEDIUM", "IN_PROGRESS"),
                                    new AssignmentTemplate("Reactions Lab Report",
                                            "Document the titration experiment.", 12, "HIGH", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-6, 13, 14, 0, 60, "COMPLETED",
                                            "Worked through bonding problems.", true),
                                    new FocusTemplate(-1, 21, 21, 30, 30, "CANCELLED",
                                            "Paused due to fatigue.", false)
                            })
                    }),
                    new PlanTemplate("Year-Long Capstone", -180, 330, false, new CourseTemplate[]{
                            new CourseTemplate("CAP401", "Capstone Project", new ChapterTemplate[]{
                                    new ChapterTemplate("Topic Selection", 10.0, 20, 100.0, -150),
                                    new ChapterTemplate("Proposal Writing", 20.0, 60, 80.0, -90),
                                    new ChapterTemplate("Implementation", 40.0, 180, 25.0, -30),
                                    new ChapterTemplate("Testing & Wrap-up", 30.0, 280, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Proposal Draft",
                                            "First full draft of the capstone proposal.", -80, "HIGH", "COMPLETED"),
                                    new AssignmentTemplate("Mid-term Demo",
                                            "Show a working prototype.", 0, "HIGH", "IN_PROGRESS"),
                                    new AssignmentTemplate("Final Submission",
                                            "Submit completed capstone package.", 120, "HIGH", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-10, 9, 12, 0, 180, "COMPLETED",
                                            "Implemented core module.", true),
                                    new FocusTemplate(-2, 14, 15, 30, 90, "COMPLETED",
                                            "Prepared mid-term demo slides.", true)
                            }),
                            new CourseTemplate("PRJ310", "Project Management", new ChapterTemplate[]{
                                    new ChapterTemplate("Planning", 30.0, 40, 100.0, -140),
                                    new ChapterTemplate("Risk Management", 35.0, 100, 60.0, -40),
                                    new ChapterTemplate("Delivery", 35.0, 200, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Risk Register",
                                            "Identify 8 project risks and mitigations.", -10, "MEDIUM", "PENDING"),
                                    new AssignmentTemplate("Status Report",
                                            "Weekly status note for stakeholders.", 7, "LOW", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-5, 10, 11, 0, 60, "COMPLETED",
                                            "Updated risk register.", true)
                            })
                    }),
                    new PlanTemplate("Spring 2026 — Archived", -200, 100, true, new CourseTemplate[]{
                            new CourseTemplate("HIS101", "World History", new ChapterTemplate[]{
                                    new ChapterTemplate("Ancient Civilisations", 40.0, 30, 100.0, -180),
                                    new ChapterTemplate("Medieval Period", 60.0, 70, 100.0, -110)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Essay: Ancient Trade Routes",
                                            "2000-word essay on the Silk Road.", -150, "MEDIUM", "COMPLETED"),
                                    new AssignmentTemplate("Medieval Timeline",
                                            "Annotated timeline of medieval Europe.", -90, "HIGH", "COMPLETED")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-130, 18, 19, 30, 90, "COMPLETED",
                                            "Drafted essay introduction.", true)
                            }),
                            new CourseTemplate("ART105", "Art Appreciation", new ChapterTemplate[]{
                                    new ChapterTemplate("Visual Elements", 50.0, 35, 100.0, -170),
                                    new ChapterTemplate("Movements", 50.0, 75, 100.0, -100)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Gallery Reflection",
                                            "Visit a local gallery and write a 500-word reflection.", -100, "LOW", "COMPLETED")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-105, 15, 16, 0, 60, "COMPLETED",
                                            "Studied impressionist works.", true)
                            })
                    }),
                    new PlanTemplate("Winter Bootcamp — Web Dev", -45, 45, false, new CourseTemplate[]{
                            new CourseTemplate("WEB210", "Frontend Foundations", new ChapterTemplate[]{
                                    new ChapterTemplate("HTML & CSS", 35.0, 10, 100.0, -25),
                                    new ChapterTemplate("JavaScript Basics", 35.0, 25, 70.0, -5),
                                    new ChapterTemplate("Responsive Layouts", 30.0, 40, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Personal Portfolio",
                                            "Build a portfolio site with HTML/CSS.", -10, "MEDIUM", "COMPLETED"),
                                    new AssignmentTemplate("JS Quiz App",
                                            "Interactive quiz built with vanilla JS.", 5, "HIGH", "IN_PROGRESS"),
                                    new AssignmentTemplate("Responsive Layout",
                                            "Make the portfolio responsive.", 25, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-7, 9, 10, 30, 90, "COMPLETED",
                                            "Implemented quiz logic.", true),
                                    new FocusTemplate(-2, 19, 19, 45, 45, "COMPLETED",
                                            "Refactored quiz components.", true)
                            }),
                            new CourseTemplate("WEB220", "Backend Basics", new ChapterTemplate[]{
                                    new ChapterTemplate("Node.js Intro", 30.0, 10, 80.0, -3),
                                    new ChapterTemplate("REST APIs", 35.0, 25, 30.0, 0),
                                    new ChapterTemplate("Databases", 35.0, 40, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("API Endpoint",
                                            "Implement CRUD endpoints.", 8, "HIGH", "PENDING"),
                                    new AssignmentTemplate("DB Schema Design",
                                            "Design a normalised schema.", 20, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-1, 20, 21, 0, 60, "COMPLETED",
                                            "Set up Node project.", true)
                            })
                    }),
                    new PlanTemplate("Language Learning — Japanese N3", -15, 120, false, new CourseTemplate[]{
                            new CourseTemplate("JPN310", "Japanese Intermediate", new ChapterTemplate[]{
                                    new ChapterTemplate("Kanji 300", 25.0, 30, 40.0, -7),
                                    new ChapterTemplate("Grammar Forms", 30.0, 60, 0.0, 0),
                                    new ChapterTemplate("Listening Drills", 25.0, 90, 0.0, 0),
                                    new ChapterTemplate("Mock Tests", 20.0, 110, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Kanji Drill",
                                            "Review 50 new kanji.", -3, "HIGH", "PENDING"),
                                    new AssignmentTemplate("Grammar Workbook",
                                            "Complete chapter 12 exercises.", 15, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-4, 7, 8, 0, 60, "COMPLETED",
                                            "Kanji flashcards.", true),
                                    new FocusTemplate(-1, 21, 22, 0, 60, "COMPLETED",
                                            "Grammar practice.", true)
                            }),
                            new CourseTemplate("JPN315", "Japanese Conversation", new ChapterTemplate[]{
                                    new ChapterTemplate("Daily Conversation", 40.0, 30, 25.0, -2),
                                    new ChapterTemplate("Speech Practice", 60.0, 75, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Shadowing Exercise",
                                            "Record a 5-minute shadowing session.", 5, "LOW", "PENDING"),
                                    new AssignmentTemplate("Speaking Partner Log",
                                            "Document a 30-min speaking session.", 18, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-2, 18, 19, 0, 60, "COMPLETED",
                                            "Conversation practice.", true)
                            })
                    }),
                    new PlanTemplate("Fitness & Nutrition Block", 0, 60, false, new CourseTemplate[]{
                            new CourseTemplate("FIT101", "Strength Training", new ChapterTemplate[]{
                                    new ChapterTemplate("Form Fundamentals", 40.0, 15, 0.0, 0),
                                    new ChapterTemplate("Programming", 60.0, 40, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Log 10 Workouts",
                                            "Record 10 strength sessions.", 30, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(2, 17, 18, 0, 60, "COMPLETED",
                                            "Lower-body session.", true)
                            }),
                            new CourseTemplate("NUT102", "Nutrition Basics", new ChapterTemplate[]{
                                    new ChapterTemplate("Macros", 50.0, 20, 0.0, 0),
                                    new ChapterTemplate("Meal Planning", 50.0, 45, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Weekly Meal Plan",
                                            "Draft a 7-day meal plan.", 14, "MEDIUM", "PENDING"),
                                    new AssignmentTemplate("Macro Tracking",
                                            "Track macros for 5 days.", 25, "LOW", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(3, 12, 13, 0, 60, "COMPLETED",
                                            "Reviewed macro guidelines.", true)
                            })
                    }),
                    new PlanTemplate("Reading Challenge — 12 Books", 10, 120, false, new CourseTemplate[]{
                            new CourseTemplate("READ200", "Non-Fiction Block", new ChapterTemplate[]{
                                    new ChapterTemplate("Book 1 — Atomic Habits", 25.0, 14, 100.0, -3),
                                    new ChapterTemplate("Book 2 — Deep Work", 25.0, 30, 60.0, 0),
                                    new ChapterTemplate("Book 3 — Thinking Fast & Slow", 25.0, 55, 0.0, 0),
                                    new ChapterTemplate("Book 4 — Range", 25.0, 90, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Atomic Habits Notes",
                                            "Capture key takeaways.", -2, "LOW", "COMPLETED"),
                                    new AssignmentTemplate("Deep Work Notes",
                                            "Capture key takeaways.", 12, "LOW", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-1, 20, 21, 0, 60, "COMPLETED",
                                            "Finished Atomic Habits.", true)
                            }),
                            new CourseTemplate("READ210", "Fiction Block", new ChapterTemplate[]{
                                    new ChapterTemplate("Book 1 — Project Hail Mary", 50.0, 25, 30.0, 0),
                                    new ChapterTemplate("Book 2 — Klara and the Sun", 50.0, 60, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Discussion Notes",
                                            "Notes for the book club meeting.", 18, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(1, 19, 20, 0, 60, "COMPLETED",
                                            "Reading session.", true)
                            })
                    }),
                    new PlanTemplate("Music Practice Plan", -10, 90, false, new CourseTemplate[]{
                            new CourseTemplate("MUS110", "Piano Practice", new ChapterTemplate[]{
                                    new ChapterTemplate("Scales & Arpeggios", 30.0, 14, 80.0, -3),
                                    new ChapterTemplate("Repertoire — Bach", 35.0, 45, 20.0, 0),
                                    new ChapterTemplate("Repertoire — Chopin", 35.0, 80, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Bach Invention Recording",
                                            "Record a clean run of Invention No. 1.", 20, "HIGH", "PENDING"),
                                    new AssignmentTemplate("Scales Quiz",
                                            "Play all major and minor scales.", 5, "MEDIUM", "IN_PROGRESS")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(-2, 17, 18, 0, 60, "COMPLETED",
                                            "Scale practice session.", true),
                                    new FocusTemplate(1, 17, 18, 0, 60, "COMPLETED",
                                            "Bach practice session.", true)
                            }),
                            new CourseTemplate("MUS120", "Music Theory", new ChapterTemplate[]{
                                    new ChapterTemplate("Harmony Basics", 50.0, 20, 50.0, 0),
                                    new ChapterTemplate("Counterpoint", 50.0, 60, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Harmony Analysis",
                                            "Analyse a chorale by Bach.", 12, "MEDIUM", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(2, 19, 20, 0, 60, "COMPLETED",
                                            "Theory study session.", true)
                            })
                    }),
                    new PlanTemplate("Career Prep — Interview Ready", 5, 75, false, new CourseTemplate[]{
                            new CourseTemplate("CAR301", "Interview Drills", new ChapterTemplate[]{
                                    new ChapterTemplate("Behavioural Stories", 40.0, 20, 0.0, 0),
                                    new ChapterTemplate("System Design", 60.0, 50, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("STAR Stories",
                                            "Prepare 5 STAR-format stories.", 14, "HIGH", "PENDING"),
                                    new AssignmentTemplate("Mock Interview",
                                            "Schedule a 60-minute mock interview.", 30, "HIGH", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(2, 10, 11, 0, 60, "COMPLETED",
                                            "Behavioural story rehearsal.", true)
                            }),
                            new CourseTemplate("CAR310", "Resume & Portfolio", new ChapterTemplate[]{
                                    new ChapterTemplate("Resume Refresh", 50.0, 14, 50.0, 0),
                                    new ChapterTemplate("Portfolio Polish", 50.0, 45, 0.0, 0)
                            }, new AssignmentTemplate[]{
                                    new AssignmentTemplate("Resume Review",
                                            "Get 2 peer reviews.", 10, "MEDIUM", "PENDING"),
                                    new AssignmentTemplate("Portfolio Case Study",
                                            "Publish one detailed case study.", 25, "HIGH", "PENDING")
                            }, new FocusTemplate[]{
                                    new FocusTemplate(1, 14, 15, 0, 60, "COMPLETED",
                                            "Resume draft.", true)
                            })
                    })
            };
        }
    }

    private record CourseTemplate(
            String code,
            String name,
            ChapterTemplate[] chapters,
            AssignmentTemplate[] assignments,
            FocusTemplate[] focusSessions
    ) {
    }

    private record ChapterTemplate(
            String name,
            double weight,
            int targetOffsetDays,
            double progress,
            int progressOffsetDays
    ) {
    }

    private record AssignmentTemplate(
            String title,
            String description,
            int dueOffsetDays,
            String priority,
            String status
    ) {
    }

    private record FocusTemplate(
            int startOffsetDays,
            int startHour,
            int endHour,
            int endMinute,
            int durationMinutes,
            String status,
            String notes,
            boolean linkedToFirstChapter
    ) {
    }

    private static boolean tableHasRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rows.next() && rows.getInt(1) > 0;
        }
    }

    private static long insertPlan(Connection connection, String name, LocalDate start, LocalDate end, boolean archived)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO study_plans(name, start_date, end_date, archived) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, start.toString());
            statement.setString(3, end.toString());
            statement.setInt(4, archived ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert study plan " + name);
    }

    private static long insertCourse(Connection connection, long studyPlanId, String code, String name)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO courses(study_plan_id, code, name) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, studyPlanId);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert course " + code);
    }

    private static long insertChapter(Connection connection, long courseId, String name, double weight, LocalDate targetDate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chapters(course_id, name, weight, target_date) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setString(2, name);
            statement.setDouble(3, weight);
            statement.setString(4, targetDate == null ? null : targetDate.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert chapter " + name);
    }

    private static void updateChapterProgress(Connection connection, long chapterId, double progress)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE chapters SET progress = ? WHERE id = ?")) {
            statement.setDouble(1, progress);
            statement.setLong(2, chapterId);
            statement.executeUpdate();
        }
    }

    private static void insertProgressRecord(Connection connection, long chapterId,
                                             double previousProgress, double newProgress, LocalDateTime recordedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO progress_records(chapter_id, previous_progress, new_progress, recorded_at) "
                        + "VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, chapterId);
            statement.setDouble(2, previousProgress);
            statement.setDouble(3, newProgress);
            statement.setString(4, recordedAt.toString());
            statement.executeUpdate();
        }
    }

    private static int insertAssignment(Connection connection, long courseId, String title, String description,
                                        LocalDate dueDate, String priority, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assignments(course_id, title, description, due_date, priority, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, courseId);
            statement.setString(2, title);
            statement.setString(3, description);
            statement.setString(4, dueDate.toString());
            statement.setString(5, priority);
            statement.setString(6, status);
            return statement.executeUpdate();
        }
    }

    private static int insertFocusSession(Connection connection, long courseId, Long chapterId,
                                          LocalDateTime startedAt, LocalDateTime endedAt,
                                          long durationSeconds, String status, String notes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO focus_sessions(course_id, chapter_id, started_at, ended_at, duration_seconds, status, notes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, courseId);
            if (chapterId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setLong(2, chapterId);
            }
            statement.setString(3, startedAt.toString());
            statement.setString(4, endedAt.toString());
            statement.setLong(5, durationSeconds);
            statement.setString(6, status);
            statement.setString(7, notes);
            return statement.executeUpdate();
        }
    }
}
