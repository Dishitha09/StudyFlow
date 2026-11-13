# Java concept mapping

Each entry points to StudyFlow code or a separate course demonstration. The stage reports record when examples were added and tested.

| Concept | Where and why | Viva explanation |
| --- | --- | --- |
| JVM, compilation and execution | `pom.xml` and `Main.main` | The Maven Compiler Plugin calls the JDK compiler to produce Java 21 bytecode. The JVM loads the bytecode and invokes `main` through the Exec plugin. |
| Packages and Java API | `com.studyflow.config`, `com.studyflow.view`, and `Main` | Packages organize classes by responsibility. `java.nio.file.Path` and `javax.swing` provide standard filesystem and desktop APIs. |
| Primitive types, variables and methods | `AppConfig.darkByDefault()` and `Main.openWindow` | A `boolean` holds one of two values. The `installed` local variable represents a setup result, and a method gives the setup action a named boundary. |
| Arrays, strings and command-line arguments | `AppConfig.fromArgs(String[])` | The runtime passes arguments as a `String` array. Checking the length before indexing makes missing arguments safe to report. |
| Static and final members | `AppConfig.APPLICATION_NAME`, `AppConfig.fromArgs` | The static method belongs to the class. The static final name is a shared constant with one value. |
| Classes, objects, encapsulation and access | `AppConfig`, `Main` and the view classes | A class defines its fields and operations. A constructor creates an instance. Configuration exposes named accessors while UI implementation details remain private. |
| Swing, AWT, JFrame, JPanel and layouts | `MainFrame`, `InitialDashboardPanel` and `Main` | A frame is the native top-level window. Panels arrange related widgets. Layout managers compute positions and sizes, and the EDT owns UI access. |
| Runnable and the EDT | `Main.main` | A lambda is a `Runnable` that captures launch configuration. `SwingUtilities.invokeLater` schedules it on Swing's event thread. |
| UML and MVC | `docs/uml` and `docs/architecture.md` | Use case, class, sequence, activity, state and component diagrams show different aspects of the same system. MVC separates widget code, UI coordination and application data or rules. |
| Constructors and constructor overloading | `GeneralTask` and `UserSettings` | A shorter constructor delegates to the full constructor with defaults. Each path uses the same validation. |
| Method overloading and object parameters | `Task.isOverdue` and `Task.addSubtask` | The date overload is deterministic in tests. Passing a Task instance to `addSubtask` preserves subtype behavior. |
| Abstract classes, inheritance, overriding and polymorphism | `Task` and the four task types | The abstract base owns shared rules. Calling a subtype's overridden method through a `Task` reference invokes the concrete implementation. |
| Interfaces and Comparable | `Searchable`, `Prioritizable`, `Identifiable`, `Storage` and `Task.compareTo` | Contracts describe behavior shared by different types. `Comparable` supplies one natural task order. |
| Static initialization, arrays and multidimensional arrays | `LanguageExamples` static block and `formatStudyGrid` | Class initialization creates the labels once. An array of arrays represents day-by-session minutes. |
| Strings, StringBuffer and StringTokenizer | `LanguageExamples.formatStudyGrid` and `importLegacyTags` | `String` is immutable. The example appends text to a mutable `StringBuffer` and reads tokens from a legacy delimited string. |
| ArrayList, LinkedList and ListIterator | `LanguageExamples.importLegacyTags` and `insertReviewBeforeExam` | A list grows as tags arrive. The agenda example uses a cursor to insert before the matching item during traversal. |
| Instance variable hiding | `LanguageExamples.Page` and `StudyPage` | Field selection uses the declared reference type. An overridden method is selected using the runtime object type. |
| Factory and type polymorphism | `TaskFactory.create` and `TaskDraft.Details` | One factory chooses the concrete task from typed creation data. Callers work through the `Task` base class. |
| Strategy and Comparator | `TaskSortStrategy` and its four implementations | A caller selects a comparison rule without changing task records or their natural order. |
| Iterator and mutable collections | `Task.editSubtask` and `TaskManager` | The child list iterator replaces a validated item in place. Task operations assemble a candidate list before committing it. |
| Generics, repository contracts and collections | `AcademicCollection<T>` and `Storage<T>` | The collection applies the same snapshot operations to subjects, workspaces and notes. The record-specific copier preserves each type and prevents accidental edits to committed state. |
| Exceptions, `try`, `catch`, `finally`, `throw` and `throws` | `Main`, `DataDirectoryLock`, `FileStorage`, `TaskNotFoundException` and `MainFrame.AppMark.paintComponent` | `IOException` is checked and callers handle or declare it. Unchecked domain exceptions report invalid use. `finally` disposes a temporary graphics context even if painting fails. |
| Thread creation, Runnable, lifecycle and ExecutorService | `Main.main`, `initialize` and `finish` | A thread factory names the worker. Tasks queue in order and file work stays off the EDT. Shutdown runs cleanup after pending writes, stops accepting work and releases the directory lock. |
| Synchronization | `TaskManager` mutations and `AcademicCollection` | Synchronized sections protect read-modify-write operations. The application worker serializes commands that touch multiple record types. |
| ActionListener and ActionEvent | `VerificationPanel` form buttons | Swing invokes an action listener when a button is activated. The controller receives parsed input and queues the corresponding use case. |
| Anonymous inner class | `Main.openWindow` | An anonymous `WindowAdapter` handles only the closing event and retains its close guard for that frame. |
| Adapter | JSON repositories implementing `Storage` | The repository adapts a validated domain snapshot to a JSON representation behind the same contract the in-memory implementation uses. |

## Later-stage and educational coverage

| Concept | Placement and viva explanation |
| --- | --- |
| MouseEvent and MouseListener | Stage 7 can add pointer-specific interactions where needed. `ActionListener` already supports mouse and keyboard activation for the Stage 6 buttons. |
| Thread priorities | `ThreadExamplesTest.explicitlyCoordinatesAThreadFromStartThroughCompletion` sets a priority and uses a latch for ordering. Priorities are hints to the JVM and operating system. |
| Singleton, Abstract Factory and Decorator | `PatternExamplesTest` uses a one-instance enum, a factory that supplies matching labels and a wrapper that adds a highlight. Production code uses explicit lifecycle ownership, one task factory and direct service dependencies. |
| Composite | A nested-folder model can use a shared interface with leaf and folder implementations. Stage 6 workspaces use ordered subject IDs and task composition models the existing tree. |
| HashMap | `PatternExamplesTest.abstractFactoryCreatesMatchingLabelsAndDecoratorAddsOneBehavior` selects a family by name. Application Kanban uses `EnumMap` for enum keys. |
