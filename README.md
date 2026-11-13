# StudyFlow

StudyFlow is a local-first Java desktop workspace for university tasks, subjects, workspaces and notes.

## Prerequisites

- JDK 21. On Windows, install or extract an x64 or ARM64 build as appropriate. Set `JAVA_HOME` to the JDK directory and put its `bin` directory on `PATH`. [Microsoft OpenJDK installation](https://learn.microsoft.com/en-us/java/openjdk/install)
- Apache Maven 3.9 or newer. Download the binary ZIP, extract it and add the Maven `bin` directory to `PATH`. A new terminal will read your updated environment. [Apache Maven installation](https://maven.apache.org/install.cgi)
- A desktop session with graphics for Swing. The first Maven build needs network access to resolve dependencies.

Verify the tools in a new terminal.

```powershell
java -version
javac -version
mvn -version
```

All three should report Java 21 for the Java toolchain. Run these commands from the directory containing `pom.xml`.

```powershell
mvn clean compile
mvn test
mvn exec:java '-Dexec.mainClass=com.studyflow.Main'
```

The window opens with the StudyFlow sidebar and initial Overview panel. Close the window to end the Maven launch.

## Project documentation

- [Software requirements](docs/SRS.md)
- [Architecture and UML](docs/architecture.md)
- [Design decisions](docs/design-decisions.md)
- [Development log](docs/development-log.md)
- [Stage reports](docs/stage-reports)
