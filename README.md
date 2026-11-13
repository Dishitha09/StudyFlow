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

If you are running this delivered copy in the original Codex workspace, you can activate its verified JDK and Maven for the current PowerShell session.

```powershell
$env:JAVA_HOME = (Get-ChildItem '..\..\work\tools\jdk' -Directory | Select-Object -First 1).FullName
$mavenBin = (Resolve-Path '..\..\work\tools\apache-maven-3.9.16\bin').Path
$env:Path = "$env:JAVA_HOME\bin;$mavenBin;$env:Path"
```

```powershell
mvn clean compile
mvn test
mvn exec:java '-Dexec.mainClass=com.studyflow.Main'
```

The initial desktop interface has tabs for Tasks, Subjects, Workspaces and Notes. Each tab lets you add records and select saved records to see their details. To check persistence, create a subject, then link it from a task, a workspace and a note. Close the window, launch StudyFlow again and select the records to see their saved details. The full management and search operations are available through the Java services and covered by the test suite.

## Local data and recovery

By default, StudyFlow uses the `data` directory next to `pom.xml`. It writes separate JSON files for tasks, subjects, workspaces, notes and settings. To launch against an alternate location, pass `--data-dir` to the application via Maven Exec.

```powershell
mvn exec:java "-Dexec.args=--data-dir 'C:/StudyFlow data'"
```

When a saved file changes, StudyFlow keeps a timestamped copy of the preceding valid file under `data/backups`. Saving requires a filesystem that supports atomic file replacement.

To back up the latest saved file, close StudyFlow and select its type (`tasks`, `subjects`, `workspaces`, `notes` or `settings`).

```powershell
mvn exec:java '-Dexec.args=--backup tasks'
```

The command validates the profile and saves an exact copy under `data/backups`, with a name like `tasks-manual-<UTC timestamp>-<uuid>.json`. It prints the full path. If the selected type has no saved file yet, the command reports an error. To back up an alternate profile, put `--data-dir` before `--backup`.

To recover a file, close StudyFlow, choose an automatic or manual backup, and pass the corresponding type. The CLI validates the backup against the current profile before replacing the file. It preserves the previous file for inspection, including damaged data.

```powershell
$backup = (Resolve-Path 'data/backups/tasks-manual-<timestamp>-<uuid>.json').Path.Replace('\', '/')
mvn exec:java "-Dexec.args=--restore tasks '$backup'"
```

Use `--data-dir` before `--restore` when recovering an alternate profile. Restart StudyFlow after a successful restore.

## Project documentation

- [Software requirements](docs/SRS.md)
- [Architecture and UML](docs/architecture.md)
- [Design decisions](docs/design-decisions.md)
- [Development log](docs/development-log.md)
- [Java concept mapping](docs/java-concept-mapping.md)
- [Stage reports](docs/stage-reports)
