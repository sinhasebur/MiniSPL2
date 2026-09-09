package com.progresspath.app;

import com.progresspath.persistence.DatabaseManager;
import com.progresspath.persistence.DatabaseSeeder;
import com.progresspath.persistence.SeedingReport;

/**
 * Headless entry point used to create the SQLite schema and load the demo
 * dataset without launching the JavaFX UI. Activated by passing
 * {@code --seed} (or {@code -s}) as the first program argument.
 *
 * <p>Typical usage (from the {@code ProgressPath} directory):</p>
 * <pre>
 *   mvn -q -DskipTests package
 *   mvn -q exec:java -Dexec.mainClass=com.progresspath.app.SeedRunner
 * </pre>
 * or, once packaged:
 * <pre>
 *   java -cp "target/classes;target/dependency/*" com.progresspath.app.SeedRunner
 * </pre>
 */
public final class SeedRunner {
    private SeedRunner() {
    }

    public static void main(String[] args) {
        DatabaseManager manager = new DatabaseManager();
        DatabaseSeeder seeder = new DatabaseSeeder(manager);
        seeder.initialize();
        SeedingReport report = seeder.seedDemoData();
        System.out.println(report.summary());
    }
}
