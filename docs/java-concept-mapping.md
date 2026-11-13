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
