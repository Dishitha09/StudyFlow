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
