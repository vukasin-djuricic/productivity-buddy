# AGENTS.md - Productivity Buddy

## Project Overview

Desktop application for real-time system monitoring, process resource analysis, and productivity tracking through process classification.

- **Language:** Java 21
- **Build System:** Gradle 8.8 (via wrapper)
- **UI Framework:** JavaFX 21.0.5 (modules: `javafx.controls`, `javafx.fxml`)
- **Module System:** JPMS (`module-info.java`)
- **Key Libraries:** ControlsFX 11.2.1, Ikonli 12.3.1, OSHI 6.6.5, SLF4J 2.0.16, JUnit Jupiter 5.10.2

## Build / Test / Run Commands

All commands use the Gradle wrapper (`./gradlew`). Run from the project root.

| Command | Description |
|---|---|
| `./gradlew build` | Compile, test, and assemble |
| `./gradlew compileJava` | Compile Java sources only |
| `./gradlew test` | Run all JUnit 5 tests |
| `./gradlew test --tests "org.productivity_buddy.SomeTest"` | Run a single test class |
| `./gradlew test --tests "org.productivity_buddy.SomeTest.testMethod"` | Run a single test method |
| `./gradlew run` | Run the application |
| `./gradlew jlink` | Build custom runtime image |
| `./gradlew jlinkZip` | Package jlink image as ZIP |
| `./gradlew clean` | Clean build artifacts |

No linting or formatting tools are configured.

## Code Style Guidelines

### Naming Conventions

- **Classes:** PascalCase (`ProcessScanner`, `MainChartView`)
- **Methods/Variables:** camelCase (`scanProcesses`, `processRegistry`)
- **Constants:** UPPER_SNAKE_CASE
- **Packages:** lowercase with underscores (`org.productivity_buddy`)
- **Enum constants:** UPPER_SNAKE_CASE (`WORK`, `FUN`, `OTHER`, `UNCATEGORIZED`)

### Formatting

- **Indentation:** 4 spaces (no tabs)
- **Braces:** K&R style — opening brace on same line as declaration
- **Line length:** No hard limit enforced; keep reasonable
- **No trailing whitespace**

### Imports

- Use fully qualified imports only when necessary to avoid ambiguity
- No wildcard imports (`*`)
- Standard Java imports first, then third-party, then project-local

### Types

- Use Java 21 features where appropriate (records, pattern matching, etc.)
- Thread-safe fields use `Atomic*` types (`AtomicLong`, `AtomicBoolean`, etc.)
- Collections use concurrent variants (`ConcurrentHashMap`) when shared across threads
- Avoid raw types; use generics with proper type parameters

### Conventions

- **Anonymous inner classes** are preferred over lambdas in JavaFX event handlers (existing codebase pattern)
- Use `Platform.runLater()` for all UI updates from background threads
- Suppress warnings sparingly; `@SuppressWarnings("unchecked")` is used where unavoidable
- Comments are written in Serbian in the existing codebase; follow this convention for consistency

### Error Handling

- Use SLF4J for logging (`org.slf4j.Logger`, `org.slf4j.LoggerFactory`)
- Log errors with context; avoid silent failures
- Use `try-catch` blocks around file I/O and system calls
- Do not expose raw stack traces in the UI

### Architecture

- **ProcessScanner** — `ForkJoinPool` with recursive `ScanTask` for parallel process enumeration
- **ProcessRegistry** — thread-safe `ConcurrentHashMap` as central shared data store
- **AnalyticsWorker** — background thread aggregating time-by-category and top-10 processes
- **FileWatcherWorker** — `WatchService` thread for hot-reloading `process_info.json`
- **FileService** — `ExecutorService`-backed async JSON/CSV file I/O
- **Views** — JavaFX: `MainChartView` (dashboard), `ProcessDetailView`, `SpecificCategoryView`

## Project Structure

```
src/main/java/org/productivity_buddy/
├── ProductivityBuddy.java        # Main JavaFX Application entry point
├── ProcessScanner.java           # ForkJoinPool-based process scanner
├── ProcessInfo.java              # Thread-safe process data model
├── ProcessCategory.java          # Enum: WORK, FUN, OTHER, UNCATEGORIZED
├── ProcessRegistry.java          # ConcurrentHashMap-based registry
├── AppConfig.java                # Properties config loader
├── FileService.java              # Async JSON save/load + CSV snapshots
├── tasks/ScanTask.java           # RecursiveAction for ForkJoinPool
├── view/                         # JavaFX UI views
│   ├── MainChartView.java
│   ├── ProcessDetailView.java
│   └── SpecificCategoryView.java
└── workers/                      # Background worker threads
    ├── AnalyticsWorker.java
    └── FileWatcherWorker.java
src/main/resources/style.css      # Dark theme CSS for JavaFX
config/config.properties          # Runtime configuration
data/                             # Runtime data (JSON + CSV snapshots)
```

## Notes

- No `.editorconfig`, Checkstyle, SpotBugs, or formatter config exists
- No existing Cursor rules, Copilot instructions, or prior AGENTS.md
- The `data/` directory contains runtime-generated files (gitignored)
- Distribution uses `jlink` via the `org.beryx.jlink` plugin (v2.25.0)
