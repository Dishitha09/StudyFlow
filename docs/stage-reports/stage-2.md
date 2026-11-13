# Stage 2 · Project foundation

## Objective

Build a standard Java 21 Maven application and launch an initial Swing window on Windows.

## Implementation

The Maven build pins FlatLaf, Jackson, JUnit Jupiter, Compiler, Surefire and Exec. `AppConfig` defines the application identity, light theme, local data path and task defaults. `Main` checks for a graphics environment, configures FlatLaf and creates the frame on the EDT. The frame provides a branded sidebar, future navigation labels, a minimum size and a central Overview panel.

## Files created

- `pom.xml`, `.gitignore` and `README.md`.
- `src/main/java/com/studyflow/Main.java`, `config/AppConfig.java`, `view/MainFrame.java` and `view/InitialDashboardPanel.java`.
- `src/test/java/com/studyflow/config/AppConfigTest.java`.
- `docs/java-concept-mapping.md` and this report.

## Files modified

- `docs/development-log.md`.
- `docs/uml/use-case-diagram.puml` and the matching PNG received a portability edit to the scope label.

## Java concepts and patterns

Maven and the JVM execute `main`. The stage demonstrates packages, records, constructors, static members, arrays for command-line arguments, Swing/AWT, layouts and the EDT. The frame and configuration establish the presentation and configuration boundaries for MVC.

## Testing

```powershell
java -version
javac -version
mvn -version
mvn clean compile
mvn test
mvn exec:java '-Dexec.mainClass=com.studyflow.Main'
```

Verified OpenJDK and `javac` 21.0.13 with Maven 3.9.16. Clean compilation succeeded. Three configuration tests passed for defaults, an alternate directory and invalid arguments. The Maven launch created a visible native window titled `StudyFlow` on Windows 11. A normal window close ended Maven with exit code 0.

## Issues and decisions

The original environment provided only a Java 8 runtime. The implementation used workspace-local Java and Maven distributions and a workspace-local Maven cache. The README explains standard Windows setup.

## Deliverables and next stage

The build, executable Swing shell, setup instructions and first tests are in place. Stage 3 can implement and test the Swing-independent domain model.
